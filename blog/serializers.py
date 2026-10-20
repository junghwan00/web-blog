from blog.models import Post
from rest_framework import serializers


class PostSerializer(serializers.ModelSerializer):
    # 작성자 아이디는 로그인한 사용자로 서버가 강제로 채움 (보안: 클라이언트가 author를 임의로 지정 못하게)
    author = serializers.PrimaryKeyRelatedField(read_only=True)
    author_name = serializers.CharField(source='author.username', read_only=True)

    class Meta:
        model = Post
        fields = (
            'id', 'author', 'author_name', 'title', 'text',
            'created_date', 'published_date', 'image',
        )
        read_only_fields = ('id', 'author', 'author_name', 'created_date', 'published_date')
