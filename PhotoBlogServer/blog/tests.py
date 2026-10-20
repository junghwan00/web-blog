import shutil
import tempfile
from io import BytesIO

from django.contrib.auth.models import User
from django.core.files.uploadedfile import SimpleUploadedFile
from django.test import override_settings
from django.utils import timezone

from rest_framework import status
from rest_framework.authtoken.models import Token
from rest_framework.test import APITestCase

from .models import Post

# 테스트 중 생성되는 업로드 파일이 실제 media/ 폴더를 더럽히지 않도록 임시 디렉터리 사용
TEST_MEDIA_ROOT = tempfile.mkdtemp(prefix="blog_test_media_")


def generate_test_image(name="test.png"):
    """1x1 PNG 더미 이미지를 담은 업로드 파일 생성 (Pillow 필요)"""
    from PIL import Image

    buf = BytesIO()
    Image.new("RGB", (1, 1), color="red").save(buf, format="PNG")
    buf.seek(0)
    return SimpleUploadedFile(name, buf.read(), content_type="image/png")


def post_payload(**overrides):
    data = {
        "title": "자동 테스트 게시물",
        "text": "APITestCase로 생성된 게시물 입니다.",
        "image": generate_test_image(),
    }
    data.update(overrides)
    return data


# 공통평가 01 - #4 "Server API 자동 테스트 코드 작성"
# ch04 TDD 수업에서 배운 unittest 패턴(TestCase 클래스 + setUp + "test_"로 시작하는 메소드)을
# Django REST framework로 만든 포토 블로그 API(/api_root/Post/)에 그대로 적용한 자동화 테스트.
@override_settings(MEDIA_ROOT=TEST_MEDIA_ROOT)
class PostAPITestCase(APITestCase):
    LIST_URL = "/api_root/Post/"

    def setUp(self):
        self.user = User.objects.create_user(username="tester", password="testpass123")
        self.other_user = User.objects.create_user(username="other", password="testpass123")
        self.token = Token.objects.create(user=self.user)
        self.other_token = Token.objects.create(user=self.other_user)

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(TEST_MEDIA_ROOT, ignore_errors=True)

    def auth(self, token):
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token.key}")

    # --- 목록/페이지네이션 ---
    def test_post_list_empty(self):
        """게시물이 하나도 없을 때 GET은 200과 페이지네이션 빈 결과를 반환해야 한다"""
        response = self.client.get(self.LIST_URL)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        body = response.json()
        self.assertEqual(body["count"], 0)
        self.assertEqual(body["results"], [])

    def test_post_list_is_paginated(self):
        """PAGE_SIZE(12)보다 많은 게시물이 있으면 next 링크가 존재해야 한다"""
        self.auth(self.token)
        for i in range(13):
            self.client.post(self.LIST_URL, post_payload(title=f"목록글 {i}"), format="multipart")
        self.client.credentials()
        response = self.client.get(self.LIST_URL)
        body = response.json()
        self.assertEqual(body["count"], 13)
        self.assertEqual(len(body["results"]), 12)
        self.assertIsNotNone(body["next"])

    # --- 생성(POST): 인증 필요 + author 강제 지정 ---
    def test_post_create_requires_authentication(self):
        """로그인(토큰) 없이 POST 하면 401 이어야 한다 (#13 보안 강화 기능)"""
        response = self.client.post(self.LIST_URL, post_payload(), format="multipart")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)
        self.assertEqual(Post.objects.count(), 0)

    def test_post_create_with_image(self):
        """토큰 인증된 멀티파트 POST -> 실제로 DB에 저장되는지 확인 (#3 POST 기능 검증)"""
        self.auth(self.token)
        response = self.client.post(self.LIST_URL, post_payload(title="해변 사진"), format="multipart")
        self.assertIn(response.status_code, (status.HTTP_200_OK, status.HTTP_201_CREATED))
        self.assertEqual(Post.objects.count(), 1)
        self.assertEqual(Post.objects.first().title, "해변 사진")

    def test_post_author_is_forced_to_logged_in_user(self):
        """요청에 author를 다른 값으로 넣어도 서버는 로그인한 사용자로 강제 지정해야 한다"""
        self.auth(self.token)
        response = self.client.post(self.LIST_URL, post_payload(author=self.other_user.id), format="multipart")
        self.assertEqual(response.status_code, status.HTTP_201_CREATED)
        created = Post.objects.get(pk=response.json()["id"])
        self.assertEqual(created.author_id, self.user.id)
        self.assertEqual(response.json()["author_name"], "tester")

    def test_post_requires_image_url_in_response(self):
        """응답 JSON에 image 절대 URL이 포함되는지 확인 (안드로이드 클라이언트가 이 URL로 이미지를 받아옴)"""
        self.auth(self.token)
        response = self.client.post(self.LIST_URL, post_payload(title="이미지 URL 확인용"), format="multipart")
        self.assertIn("image", response.json())
        self.assertTrue(response.json()["image"])

    def test_post_list_after_create(self):
        """생성한 게시물이 목록 조회(GET)에 실제로 나타나는지 확인 (#7~9 동기화 기능의 전제조건)"""
        self.auth(self.token)
        self.client.post(self.LIST_URL, post_payload(title="목록 확인용 게시물"), format="multipart")
        self.client.credentials()
        response = self.client.get(self.LIST_URL)
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        titles = [item["title"] for item in response.json()["results"]]
        self.assertIn("목록 확인용 게시물", titles)

    # --- 검색/정렬 (#13 편의기능) ---
    def test_search_filters_by_title_or_text(self):
        self.auth(self.token)
        self.client.post(self.LIST_URL, post_payload(title="호수의 선착장", text="나무 선착장"), format="multipart")
        self.client.post(self.LIST_URL, post_payload(title="해변", text="파도가 밀려오는 해변"), format="multipart")
        self.client.credentials()
        response = self.client.get(self.LIST_URL, {"search": "해변"})
        results = response.json()["results"]
        self.assertEqual(len(results), 1)
        self.assertEqual(results[0]["title"], "해변")

    def test_ordering_by_title(self):
        self.auth(self.token)
        self.client.post(self.LIST_URL, post_payload(title="나"), format="multipart")
        self.client.post(self.LIST_URL, post_payload(title="가"), format="multipart")
        self.client.credentials()
        response = self.client.get(self.LIST_URL, {"ordering": "title"})
        titles = [item["title"] for item in response.json()["results"]]
        self.assertEqual(titles, sorted(titles))

    # --- 수정/삭제 권한 (#13 편의기능: 본인 글만 수정/삭제) ---
    def test_owner_can_update_own_post(self):
        self.auth(self.token)
        create_resp = self.client.post(self.LIST_URL, post_payload(title="원본 제목"), format="multipart")
        post_id = create_resp.json()["id"]
        patch_resp = self.client.patch(f"{self.LIST_URL}{post_id}/", {"title": "수정된 제목"})
        self.assertEqual(patch_resp.status_code, status.HTTP_200_OK)
        self.assertEqual(Post.objects.get(pk=post_id).title, "수정된 제목")

    def test_other_user_cannot_update_post(self):
        """서버는 다른 사용자의 수정을 차단해야 한다 (증빙 14-2 요구사항)"""
        self.auth(self.token)
        create_resp = self.client.post(self.LIST_URL, post_payload(title="원본 제목"), format="multipart")
        post_id = create_resp.json()["id"]

        self.auth(self.other_token)
        patch_resp = self.client.patch(f"{self.LIST_URL}{post_id}/", {"title": "해킹 시도"})
        self.assertEqual(patch_resp.status_code, status.HTTP_403_FORBIDDEN)
        self.assertEqual(Post.objects.get(pk=post_id).title, "원본 제목")

    def test_unauthenticated_cannot_update_post(self):
        self.auth(self.token)
        create_resp = self.client.post(self.LIST_URL, post_payload(title="원본 제목"), format="multipart")
        post_id = create_resp.json()["id"]

        self.client.credentials()
        patch_resp = self.client.patch(f"{self.LIST_URL}{post_id}/", {"title": "익명 시도"})
        self.assertEqual(patch_resp.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_owner_can_delete_own_post(self):
        self.auth(self.token)
        create_resp = self.client.post(self.LIST_URL, post_payload(title="삭제될 글"), format="multipart")
        post_id = create_resp.json()["id"]
        delete_resp = self.client.delete(f"{self.LIST_URL}{post_id}/")
        self.assertEqual(delete_resp.status_code, status.HTTP_204_NO_CONTENT)
        self.assertFalse(Post.objects.filter(pk=post_id).exists())

    def test_other_user_cannot_delete_post(self):
        self.auth(self.token)
        create_resp = self.client.post(self.LIST_URL, post_payload(title="보호될 글"), format="multipart")
        post_id = create_resp.json()["id"]

        self.auth(self.other_token)
        delete_resp = self.client.delete(f"{self.LIST_URL}{post_id}/")
        self.assertEqual(delete_resp.status_code, status.HTTP_403_FORBIDDEN)
        self.assertTrue(Post.objects.filter(pk=post_id).exists())


# --- 로그인/me 엔드포인트 (#13 편의기능: 서버 설정 - 로그인) ---
class AuthEndpointTestCase(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username="tester", password="testpass123")

    def test_login_returns_token(self):
        response = self.client.post("/api_root/login/", {"username": "tester", "password": "testpass123"})
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertIn("token", response.json())

    def test_login_with_wrong_password_fails(self):
        response = self.client.post("/api_root/login/", {"username": "tester", "password": "wrong"})
        self.assertEqual(response.status_code, status.HTTP_400_BAD_REQUEST)

    def test_me_requires_authentication(self):
        response = self.client.get("/api_root/me/")
        self.assertEqual(response.status_code, status.HTTP_401_UNAUTHORIZED)

    def test_me_returns_username_when_authenticated(self):
        token = Token.objects.create(user=self.user)
        self.client.credentials(HTTP_AUTHORIZATION=f"Token {token.key}")
        response = self.client.get("/api_root/me/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertEqual(response.json()["username"], "tester")


@override_settings(MEDIA_ROOT=TEST_MEDIA_ROOT)
class PostListPageTestCase(APITestCase):
    """blog.views.post_list (일반 HTML 화면) 자체 테스트"""

    def setUp(self):
        self.user = User.objects.create_user(username="tester2", password="testpass123")

    @classmethod
    def tearDownClass(cls):
        super().tearDownClass()
        shutil.rmtree(TEST_MEDIA_ROOT, ignore_errors=True)

    def test_post_list_page_status_ok(self):
        response = self.client.get("/")
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_post_list_page_shows_published_post(self):
        Post.objects.create(
            author=self.user,
            title="화면 테스트용 글",
            text="post_list 뷰에 보이는지 확인",
            published_date=timezone.now(),
        )
        response = self.client.get("/")
        self.assertContains(response, "화면 테스트용 글")

    def test_post_detail_page(self):
        post = Post.objects.create(
            author=self.user, title='상세 테스트', text='본문', published_date=timezone.now(),
        )
        response = self.client.get(f'/post/{post.pk}/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)
        self.assertContains(response, '상세 테스트')

    def test_post_detail_404_for_missing_post(self):
        response = self.client.get('/post/99999/')
        self.assertEqual(response.status_code, status.HTTP_404_NOT_FOUND)

    def test_post_new_get_shows_form(self):
        response = self.client.get('/post/new/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_post_new_post_creates_and_redirects(self):
        self.client.force_login(self.user)
        response = self.client.post('/post/new/', {'title': '새 글', 'text': '내용'})
        self.assertEqual(response.status_code, status.HTTP_302_FOUND)
        self.assertTrue(Post.objects.filter(title='새 글').exists())

    def test_post_edit_get_shows_form(self):
        post = Post.objects.create(
            author=self.user, title='수정 전', text='본문', published_date=timezone.now(),
        )
        response = self.client.get(f'/post/{post.pk}/edit/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)

    def test_post_edit_post_updates(self):
        post = Post.objects.create(
            author=self.user, title='수정 전', text='본문', published_date=timezone.now(),
        )
        self.client.force_login(self.user)
        response = self.client.post(f'/post/{post.pk}/edit/', {'title': '수정 후', 'text': '본문2'})
        self.assertEqual(response.status_code, status.HTTP_302_FOUND)
        post.refresh_from_db()
        self.assertEqual(post.title, '수정 후')

    def test_js_test_page(self):
        response = self.client.get('/js_test/')
        self.assertEqual(response.status_code, status.HTTP_200_OK)


class PostModelTestCase(APITestCase):
    def setUp(self):
        self.user = User.objects.create_user(username='modeltester', password='testpass123')

    def test_str_returns_title(self):
        post = Post(author=self.user, title='문자열 테스트', text='x')
        self.assertEqual(str(post), '문자열 테스트')

    def test_publish_sets_published_date(self):
        post = Post.objects.create(author=self.user, title='발행 테스트', text='x')
        self.assertIsNone(post.published_date)
        post.publish()
        self.assertIsNotNone(post.published_date)
