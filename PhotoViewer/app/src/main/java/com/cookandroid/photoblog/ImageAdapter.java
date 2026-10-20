package com.cookandroid.photoblog;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

// 동기화(GET /api_root/Post/)로 받아온 게시물 목록을 RecyclerView에 표시.
// 즐겨찾기 토글, 본인 글 수정/삭제, 사진 크게 보기(미리보기)를 지원한다. (#13 편의기능)
public class ImageAdapter extends RecyclerView.Adapter<ImageAdapter.ImageViewHolder> {

    public interface Listener {
        void onToggleFavorite(PostItem item);

        void onEdit(PostItem item);

        void onDelete(PostItem item);

        void onImageClick(PostItem item);
    }

    private final List<PostItem> items;
    private final Listener listener;
    private String currentUsername;
    private java.util.Set<String> favoriteIds = new java.util.HashSet<>();

    public ImageAdapter(List<PostItem> items, Listener listener) {
        this.items = items;
        this.listener = listener;
    }

    public void setCurrentUsername(String username) {
        this.currentUsername = username;
    }

    public void setFavoriteIds(java.util.Set<String> ids) {
        this.favoriteIds = ids;
    }

    public static class ImageViewHolder extends RecyclerView.ViewHolder {
        ImageView imageView;
        TextView titleView;
        TextView authorView;
        TextView textView;
        Button favoriteButton;
        View ownerActionsRow;
        Button editButton;
        Button deleteButton;

        public ImageViewHolder(View itemView) {
            super(itemView);
            imageView = itemView.findViewById(R.id.imageViewItem);
            titleView = itemView.findViewById(R.id.titleViewItem);
            authorView = itemView.findViewById(R.id.authorViewItem);
            textView = itemView.findViewById(R.id.textViewItem);
            favoriteButton = itemView.findViewById(R.id.favoriteButton);
            ownerActionsRow = itemView.findViewById(R.id.ownerActionsRow);
            editButton = itemView.findViewById(R.id.editButton);
            deleteButton = itemView.findViewById(R.id.deleteButton);
        }
    }

    @NonNull
    @Override
    public ImageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_image, parent, false);
        return new ImageViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ImageViewHolder holder, int position) {
        PostItem item = items.get(position);
        holder.titleView.setText(item.title);
        holder.authorView.setText(item.authorName + " · " + item.publishedDate);
        holder.textView.setText(item.text);

        if (item.bitmap != null) {
            holder.imageView.setImageBitmap(item.bitmap);
        } else {
            holder.imageView.setImageDrawable(null);
        }
        holder.imageView.setOnClickListener(v -> {
            if (listener != null) listener.onImageClick(item);
        });

        boolean isFav = favoriteIds.contains(String.valueOf(item.id));
        holder.favoriteButton.setText(isFav ? "★" : "☆");
        holder.favoriteButton.setOnClickListener(v -> {
            if (listener != null) listener.onToggleFavorite(item);
        });

        boolean isOwner = currentUsername != null && currentUsername.equals(item.authorName);
        holder.ownerActionsRow.setVisibility(isOwner ? View.VISIBLE : View.GONE);
        holder.editButton.setOnClickListener(v -> {
            if (listener != null) listener.onEdit(item);
        });
        holder.deleteButton.setOnClickListener(v -> {
            if (listener != null) listener.onDelete(item);
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }
}
