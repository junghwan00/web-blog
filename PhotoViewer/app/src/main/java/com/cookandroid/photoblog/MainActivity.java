package com.cookandroid.photoblog;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.MediaStore;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

// 공통평가 01 - #7,8,9 Server/Client 연동 테스트, #10 Upload(폼 UI), #13 기타 편의기능 5종:
//  14-1 검색/정렬/페이지네이션, 14-2 본인 글 수정/삭제, 14-3 즐겨찾기+오프라인 캐시,
//  14-4 작성 초안 저장/복원, 14-5 서버 주소/로그인(토큰 암호화 저장).
public class MainActivity extends AppCompatActivity implements ImageAdapter.Listener {

    private static final int READ_MEDIA_IMAGES_PERMISSION_CODE = 1001;
    private static final int READ_EXTERNAL_STORAGE_PERMISSION_CODE = 1002;
    private static final String[] SORT_LABELS = {"최신순", "오래된순", "제목순"};
    private static final String[] SORT_VALUES = {"-published_date", "published_date", "title"};

    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    private final Handler handler = new Handler(Looper.getMainLooper());

    private Prefs prefs;
    private RecyclerView recyclerView;
    private TextView statusText;
    private EditText searchInput;
    private CheckBox favoriteFilterCheck;
    private Spinner sortSpinner;
    private Button btnLoadMore;

    private final List<PostItem> allItems = new ArrayList<>();
    private final List<PostItem> displayedItems = new ArrayList<>();
    private ImageAdapter adapter;
    private String nextPageUrl;

    private String pendingImagePath;
    private ImageView activeDialogPreview;
    private PostItem editingItem;

    private final ActivityResultLauncher<Intent> imagePickerLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null) {
                    Uri imageUri = result.getData().getData();
                    pendingImagePath = getRealPathFromURI(imageUri);
                    if (activeDialogPreview != null && pendingImagePath != null) {
                        activeDialogPreview.setImageBitmap(
                                android.graphics.BitmapFactory.decodeFile(pendingImagePath));
                    }
                }
            }
    );

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = new Prefs(this);

        statusText = findViewById(R.id.statusText);
        recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ImageAdapter(displayedItems, this);
        adapter.setCurrentUsername(prefs.getUsername());
        adapter.setFavoriteIds(prefs.getFavoriteIds());
        recyclerView.setAdapter(adapter);

        searchInput = findViewById(R.id.searchInput);
        favoriteFilterCheck = findViewById(R.id.favoriteFilterCheck);
        sortSpinner = findViewById(R.id.sortSpinner);
        btnLoadMore = findViewById(R.id.btnLoadMore);

        ArrayAdapter<String> sortAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, SORT_LABELS);
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        sortSpinner.setAdapter(sortAdapter);
        sortSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                syncFromServer(true);
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
            }
        });

        findViewById(R.id.btnSync).setOnClickListener(v -> syncFromServer(true));
        findViewById(R.id.btnUpload).setOnClickListener(v -> onClickUpload());
        findViewById(R.id.btnSettings).setOnClickListener(v -> SettingsDialog.show(this, prefs, () -> {
            adapter.setCurrentUsername(prefs.getUsername());
            refreshDisplayedList();
            syncFromServer(true);
        }));
        findViewById(R.id.btnSearch).setOnClickListener(v -> syncFromServer(true));
        favoriteFilterCheck.setOnCheckedChangeListener((btn, checked) -> refreshDisplayedList());
        btnLoadMore.setOnClickListener(v -> syncFromServer(false));

        // 최초 진입: 네트워크 동기화를 시도하되, 실패하면 오프라인 캐시를 보여준다 (#14-3)
        syncFromServer(true);
    }

    // #7,#8,#9 Server/Client 연동 테스트 + #14-1 검색/정렬/페이지네이션 + #14-3 오프라인 캐시
    private void syncFromServer(boolean resetPage) {
        String baseUrl = prefs.getServerAddress();
        String search = searchInput.getText().toString().trim();
        String ordering = SORT_VALUES[Math.max(sortSpinner.getSelectedItemPosition(), 0)];
        String requestNextUrl = resetPage ? null : nextPageUrl;

        if (resetPage) {
            statusText.setText("동기화 중...");
        } else {
            statusText.setText("다음 페이지 불러오는 중...");
        }

        executorService.execute(() -> {
            ApiClient.PageResult result = ApiClient.fetchPage(baseUrl, requestNextUrl, search, ordering);

            if (result.error == null) {
                for (PostItem item : result.items) {
                    Bitmap cached = OfflineCache.loadCachedImage(this, item.imageUrl);
                    item.bitmap = cached != null ? cached : ApiClient.downloadBitmap(item.imageUrl);
                    if (cached == null && item.bitmap != null) {
                        OfflineCache.cacheImage(this, item.imageUrl, item.bitmap);
                    }
                }
            }

            handler.post(() -> {
                if (result.error != null) {
                    if (resetPage) {
                        // 네트워크 실패 -> 저장된 사진 목록 유지 (#14-3)
                        List<PostItem> cachedItems = OfflineCache.loadListCache(this, baseUrl);
                        allItems.clear();
                        allItems.addAll(cachedItems);
                        for (PostItem item : allItems) {
                            item.bitmap = OfflineCache.loadCachedImage(this, item.imageUrl);
                        }
                        nextPageUrl = null;
                        btnLoadMore.setVisibility(View.GONE);
                        refreshDisplayedList();
                        statusText.setText(result.error + " · 저장된 사진 " + allItems.size() + "개 유지 · 동기화로 재시도");
                    } else {
                        statusText.setText(result.error);
                    }
                    return;
                }

                if (resetPage) {
                    allItems.clear();
                }
                allItems.addAll(result.items);
                nextPageUrl = result.nextPageUrl;
                btnLoadMore.setVisibility(nextPageUrl != null ? View.VISIBLE : View.GONE);
                refreshDisplayedList();

                OfflineCache.saveListCache(this, baseUrl, allItems);

                statusText.setText(allItems.isEmpty()
                        ? "불러올 이미지가 없습니다."
                        : "동기화 완료 · " + allItems.size() + "/" + result.count + "개의 사진");
            });
        });
    }

    private void refreshDisplayedList() {
        adapter.setFavoriteIds(prefs.getFavoriteIds());
        displayedItems.clear();
        if (favoriteFilterCheck.isChecked()) {
            Set<String> favIds = prefs.getFavoriteIds();
            for (PostItem item : allItems) {
                if (favIds.contains(String.valueOf(item.id))) displayedItems.add(item);
            }
        } else {
            displayedItems.addAll(allItems);
        }
        adapter.notifyDataSetChanged();
    }

    // --- ImageAdapter.Listener ---
    @Override
    public void onToggleFavorite(PostItem item) {
        prefs.toggleFavorite(item.id);
        refreshDisplayedList();
    }

    @Override
    public void onEdit(PostItem item) {
        if (!prefs.isLoggedIn()) {
            Toast.makeText(this, "수정하려면 설정에서 로그인하세요.", Toast.LENGTH_SHORT).show();
            return;
        }
        showPostForm(item);
    }

    @Override
    public void onDelete(PostItem item) {
        new AlertDialog.Builder(this)
                .setTitle("이 사진을 삭제할까요?")
                .setMessage(item.title + "\n게시글과 사진을 삭제합니다. 이 작업은 되돌릴 수 없습니다.")
                .setNegativeButton("취소", null)
                .setPositiveButton("삭제 확인", (d, w) -> {
                    String token = prefs.getToken();
                    executorService.execute(() -> {
                        ApiClient.ActionResult result = ApiClient.deletePost(prefs.getServerAddress(), token, item.id);
                        handler.post(() -> {
                            if (result.success) {
                                allItems.remove(item);
                                refreshDisplayedList();
                                OfflineCache.saveListCache(this, prefs.getServerAddress(), allItems);
                                Toast.makeText(this, "삭제되었습니다.", Toast.LENGTH_SHORT).show();
                            } else {
                                Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                            }
                        });
                    });
                })
                .show();
    }

    @Override
    public void onImageClick(PostItem item) {
        ImageView full = new ImageView(this);
        full.setAdjustViewBounds(true);
        if (item.bitmap != null) full.setImageBitmap(item.bitmap);
        new AlertDialog.Builder(this)
                .setTitle(item.title)
                .setView(full)
                .setPositiveButton("닫기", null)
                .show();
    }

    // --- 게시글 작성/수정 폼 (#10 Upload UI 제공 방식, #14-2 수정, #14-4 초안) ---
    private void onClickUpload() {
        if (!prefs.isLoggedIn()) {
            Toast.makeText(this, "글을 올리려면 설정에서 먼저 로그인하세요.", Toast.LENGTH_LONG).show();
            return;
        }
        showPostForm(null);
    }

    private void showPostForm(PostItem editItem) {
        this.editingItem = editItem;
        boolean isEdit = editItem != null;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int pad = dp(20);
        root.setPadding(pad, pad, pad, pad);

        ImageView preview = new ImageView(this);
        preview.setAdjustViewBounds(true);
        preview.setMaxHeight(dp(180));
        LinearLayout.LayoutParams previewParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(160));
        root.addView(preview, previewParams);
        activeDialogPreview = preview;
        pendingImagePath = null;

        Button pickImageBtn = new Button(this);
        pickImageBtn.setText(isEdit ? "사진은 그대로 유지됩니다" : "사진 선택");
        pickImageBtn.setEnabled(!isEdit);
        root.addView(pickImageBtn);

        EditText titleInput = new EditText(this);
        titleInput.setHint("제목");
        root.addView(titleInput);
        TextView titleCounter = new TextView(this);
        root.addView(titleCounter);

        EditText textInput = new EditText(this);
        textInput.setHint("내용");
        textInput.setMinLines(3);
        root.addView(textInput);
        TextView textCounter = new TextView(this);
        root.addView(textCounter);

        final int titleMax = 200;
        final int textMax = 5000;
        titleInput.addTextChangedListener(counterWatcher(titleCounter, titleMax));
        textInput.addTextChangedListener(counterWatcher(textCounter, textMax));

        if (isEdit) {
            titleInput.setText(editItem.title);
            textInput.setText(editItem.text);
        } else if (prefs.hasDraft()) {
            titleInput.setText(prefs.getDraftTitle());
            textInput.setText(prefs.getDraftText());
            pendingImagePath = prefs.getDraftImagePath();
            if (pendingImagePath != null) {
                preview.setImageBitmap(android.graphics.BitmapFactory.decodeFile(pendingImagePath));
            }
        }
        titleCounter.setText(titleInput.length() + "/" + titleMax);
        textCounter.setText(textInput.length() + "/" + textMax);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(isEdit ? "게시글 수정" : "새 사진 게시")
                .setView(root)
                .setNegativeButton(isEdit ? "취소" : "닫기·초안 저장", (d, w) -> {
                    if (!isEdit) {
                        prefs.saveDraft(titleInput.getText().toString(), textInput.getText().toString(), pendingImagePath);
                    }
                });

        if (!isEdit) {
            builder.setNeutralButton("초안 삭제", (d, w) -> prefs.clearDraft());
        }

        builder.setPositiveButton(isEdit ? "수정 저장" : "업로드", null);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            pickImageBtn.setOnClickListener(v -> onClickPickImage());
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                String title = titleInput.getText().toString().trim();
                String text = textInput.getText().toString().trim();
                if (title.isEmpty()) {
                    Toast.makeText(this, "제목을 입력하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!isEdit && pendingImagePath == null) {
                    Toast.makeText(this, "사진을 선택하세요.", Toast.LENGTH_SHORT).show();
                    return;
                }
                submitPostForm(isEdit, editItem, title, text);
                dialog.dismiss();
            });
        });
        dialog.show();
    }

    private void onClickPickImage() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                        READ_MEDIA_IMAGES_PERMISSION_CODE);
            } else {
                openImagePicker();
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                        READ_EXTERNAL_STORAGE_PERMISSION_CODE);
            } else {
                openImagePicker();
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == READ_MEDIA_IMAGES_PERMISSION_CODE || requestCode == READ_EXTERNAL_STORAGE_PERMISSION_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openImagePicker();
            } else {
                Toast.makeText(this, "권한이 거부되었습니다.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        imagePickerLauncher.launch(intent);
    }

    private void submitPostForm(boolean isEdit, PostItem editItem, String title, String text) {
        String token = prefs.getToken();
        statusText.setText(isEdit ? "수정 중..." : "업로드 중...");
        String imagePath = pendingImagePath;
        executorService.execute(() -> {
            ApiClient.ActionResult result = isEdit
                    ? ApiClient.updatePost(prefs.getServerAddress(), token, editItem.id, title, text)
                    : ApiClient.createPost(prefs.getServerAddress(), token, title, text, imagePath);
            handler.post(() -> {
                if (result.success) {
                    statusText.setText((isEdit ? "수정 완료" : "업로드 성공") + " (HTTP " + result.httpCode + ")");
                    if (!isEdit) prefs.clearDraft();
                    syncFromServer(true);
                } else {
                    statusText.setText(result.error);
                    Toast.makeText(this, result.error, Toast.LENGTH_LONG).show();
                }
            });
        });
    }

    private TextWatcher counterWatcher(TextView counterView, int max) {
        return new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                counterView.setText(s.length() + "/" + max);
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        };
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] projection = {MediaStore.Images.Media.DATA};
        Cursor cursor = getContentResolver().query(contentUri, projection, null, null, null);
        if (cursor == null) {
            return contentUri.getPath();
        }
        try {
            if (cursor.moveToFirst()) {
                int columnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
                return cursor.getString(columnIndex);
            }
            return null;
        } finally {
            cursor.close();
        }
    }

    private int dp(int value) {
        float density = getResources().getDisplayMetrics().density;
        return Math.round(value * density);
    }
}
