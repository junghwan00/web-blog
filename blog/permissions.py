from rest_framework import permissions


class IsAuthorOrReadOnly(permissions.BasePermission):
    """
    GET/HEAD/OPTIONS 는 누구나 허용.
    POST(생성)는 로그인(토큰 인증)한 사용자만 허용.
    PUT/PATCH/DELETE(수정/삭제)는 로그인 + 본인 게시글(author)인 경우만 허용.
    -> 증빙 14-2 '서버는 다른 사용자의 수정을 차단합니다' 요구사항을 서버에서 실제로 강제한다.
    """

    def has_permission(self, request, view):
        if request.method in permissions.SAFE_METHODS:
            return True
        return bool(request.user and request.user.is_authenticated)

    def has_object_permission(self, request, view, obj):
        if request.method in permissions.SAFE_METHODS:
            return True
        return bool(request.user and request.user.is_authenticated and obj.author_id == request.user.id)
