# PhotoBlogProject — 모바일/웹서비스 프로젝트 공통평가 01

- `PhotoBlogServer/` : Django REST Framework 기반 포토 블로그 서버
- `PhotoViewer/` : Android(Java) 클라이언트 앱
- `모바일웹서비스 프로젝트_공통평가 01_수행 결과 보고서.docx` : 수행 결과 보고서

## 서버 실행
```bash
cd PhotoBlogServer
source venv/bin/activate
pip install -r requirements.txt
python manage.py migrate
python manage.py runserver
```
관리자 계정: `ADMIN_CREDENTIALS.txt` 참고

## 안드로이드 앱
`PhotoViewer/` 를 Android Studio로 열어서 실행 (minSdk 33). 에뮬레이터에서 로컬 서버는 `http://10.0.2.2:8000` 으로 접근.

## 주요 기능
- 사진 목록 조회(검색/정렬/페이지네이션), 사진 게시(폼 UI), 본인 글 수정/삭제(토큰 로그인 필요)
- 즐겨찾기 + 오프라인 캐시(목록 4MB, 사진 32MB 이내), 작성 초안 저장/복원
- 서버 주소 변경 + 로그인(토큰은 Android Keystore로 암호화하여 저장)
