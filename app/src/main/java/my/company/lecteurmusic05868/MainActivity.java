package my.company.lecteurmusic05868;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import androidx.appcompat.widget.SearchView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.content.res.ResourcesCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private MediaPlayer mediaPlayer;
    private RecyclerView recyclerView;
    private SearchView searchView;
    private ImageButton btnPlayPause, btnPrevious, btnNext;
    private TextView songTitle;
    private SeekBar musicSeekbar;
    private TextView currentTime, durationTime;
    private Handler handler = new Handler();
    private List<MusicItem> musicItems;
    private List<MusicItem> allMusicItems;
    private MusicAdapter musicAdapter;
    private int currentSongIndex = 0;
    private boolean isPlaying = false;
    private boolean isUserSeeking = false;

    private static class MusicItem {
        Uri uri;
        String name;
        
        MusicItem(Uri uri, String name) {
            this.uri = uri;
            this.name = name;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
        applyMinecraftFont();
        checkPermissions();
        setupMediaPlayer();
        setupListeners();
        setupSeekBar();
    }

    private void initViews() {
        recyclerView = findViewById(R.id.recycler_view);
        searchView = findViewById(R.id.search_view);
        btnPlayPause = findViewById(R.id.btn_play_pause);
        btnPrevious = findViewById(R.id.btn_previous);
        btnNext = findViewById(R.id.btn_next);
        songTitle = findViewById(R.id.song_title);
        musicSeekbar = findViewById(R.id.music_seekbar);
        currentTime = findViewById(R.id.current_time);
        durationTime = findViewById(R.id.duration_time);
        
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        musicAdapter = new MusicAdapter();
        recyclerView.setAdapter(musicAdapter);
    }

    private void applyMinecraftFont() {
        android.graphics.Typeface minecraftFont = ResourcesCompat.getFont(this, R.font.minecraft);
        if (minecraftFont != null) {
            songTitle.setTypeface(minecraftFont);
            currentTime.setTypeface(minecraftFont);
            durationTime.setTypeface(minecraftFont);
        }
    }

    private void setupSeekBar() {
        musicSeekbar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && mediaPlayer != null) {
                    mediaPlayer.seekTo(progress);
                    currentTime.setText(formatTime(progress));
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                isUserSeeking = true;
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                isUserSeeking = false;
                if (mediaPlayer != null) {
                    mediaPlayer.seekTo(seekBar.getProgress());
                }
            }
        });
    }

    private void updateSeekBar() {
        if (mediaPlayer != null && mediaPlayer.isPlaying() && !isUserSeeking) {
            int currentPosition = mediaPlayer.getCurrentPosition();
            musicSeekbar.setProgress(currentPosition);
            currentTime.setText(formatTime(currentPosition));
            handler.postDelayed(this::updateSeekBar, 1000);
        }
    }

    private String formatTime(int milliseconds) {
        if (milliseconds < 0) return "0:00";
        int minutes = (milliseconds / 1000) / 60;
        int seconds = (milliseconds / 1000) % 60;
        return String.format("%d:%02d", minutes, seconds);
    }

    private void checkPermissions() {
        String permission;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permission = Manifest.permission.READ_MEDIA_AUDIO;
        } else {
            permission = Manifest.permission.READ_EXTERNAL_STORAGE;
        }
        
        if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{permission}, 1);
        } else {
            loadMusicFiles();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 1 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            loadMusicFiles();
        } else {
            Toast.makeText(this, "Permission requise pour accéder à la musique", Toast.LENGTH_LONG).show();
        }
    }

    private void loadMusicFiles() {
        musicItems = new ArrayList<>();
        allMusicItems = new ArrayList<>();
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            loadMusicFromMediaStore();
        } else {
            loadMusicFromFileSystem();
        }
        
        allMusicItems.addAll(musicItems);
        musicAdapter.submitList(new ArrayList<>(musicItems));
        
        if (musicItems.isEmpty()) {
            songTitle.setText("Aucune musique trouvée");
        }
    }

    private void loadMusicFromMediaStore() {
        String[] projection = {
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.DISPLAY_NAME
        };
        
        String selection = MediaStore.Audio.Media.IS_MUSIC + " != 0";
        
        try (Cursor cursor = getContentResolver().query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            null,
            MediaStore.Audio.Media.DISPLAY_NAME + " ASC"
        )) {
            if (cursor != null) {
                int idIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID);
                int nameIndex = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DISPLAY_NAME);
                
                while (cursor.moveToNext()) {
                    long id = cursor.getLong(idIndex);
                    String name = cursor.getString(nameIndex);
                    
                    if (name.toLowerCase().endsWith(".mp3") || 
                        name.toLowerCase().endsWith(".m4a") ||
                        name.toLowerCase().endsWith(".wav")) {
                        
                        Uri uri = Uri.withAppendedPath(
                            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                            String.valueOf(id)
                        );
                        musicItems.add(new MusicItem(uri, name));
                    }
                }
            }
        } catch (Exception e) {
            Toast.makeText(this, "Erreur lors du chargement", Toast.LENGTH_SHORT).show();
        }
    }

    private void loadMusicFromFileSystem() {
        File musicDirectory = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC);
        if (musicDirectory != null && musicDirectory.exists()) {
            findMusicFiles(musicDirectory);
        }
    }

    private void findMusicFiles(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    findMusicFiles(file);
                } else {
                    String name = file.getName().toLowerCase();
                    if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".wav")) {
                        Uri uri = Uri.fromFile(file);
                        musicItems.add(new MusicItem(uri, file.getName()));
                    }
                }
            }
        }
    }

    private void setupMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setOnPreparedListener(mp -> {
            if (isPlaying) {
                mp.start();
                        btnPlayPause.setImageResource(R.drawable.ic_pause);
            }
            
            int duration = mp.getDuration();
            if (duration > 0) {
                musicSeekbar.setMax(duration);
                durationTime.setText(formatTime(duration));
            }
            updateSeekBar();
        });
        mediaPlayer.setOnCompletionListener(mp -> playNext());
        mediaPlayer.setOnErrorListener((mp, what, extra) -> {
            Toast.makeText(MainActivity.this, "Erreur lecture", Toast.LENGTH_SHORT).show();
            return true;
        });
    }

    private void setupListeners() {
        btnPlayPause.setOnClickListener(v -> togglePlayPause());
        btnPrevious.setOnClickListener(v -> playPrevious());
        btnNext.setOnClickListener(v -> playNext());
        
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) { return false; }

            @Override
            public boolean onQueryTextChange(String newText) {
                filterMusicList(newText);
                return true;
            }
        });

        musicAdapter.setOnItemClickListener(position -> {
            currentSongIndex = position;
            playSong(currentSongIndex);
        });
    }

    private void togglePlayPause() {
        if (mediaPlayer == null) return;
        
        if (mediaPlayer.isPlaying()) {
            mediaPlayer.pause();
            btnPlayPause.setImageResource(R.drawable.ic_play);
            isPlaying = false;
            handler.removeCallbacksAndMessages(null);
        } else {
            mediaPlayer.start();
            btnPlayPause.setImageResource(R.drawable.ic_pause);
            isPlaying = true;
            updateSeekBar();
        }
    }

    private void playPrevious() {
        if (musicItems != null && !musicItems.isEmpty()) {
            currentSongIndex = (currentSongIndex - 1 + musicItems.size()) % musicItems.size();
            playSong(currentSongIndex);
        } else {
            Toast.makeText(this, "Aucune musique", Toast.LENGTH_SHORT).show();
        }
    }

    private void playNext() {
        if (musicItems != null && !musicItems.isEmpty()) {
            currentSongIndex = (currentSongIndex + 1) % musicItems.size();
            playSong(currentSongIndex);
        } else {
            Toast.makeText(this, "Aucune musique", Toast.LENGTH_SHORT).show();
        }
    }

    private void playSong(int index) {
        if (musicItems == null || musicItems.isEmpty()) return;
        
        try {
            mediaPlayer.reset();
            isPlaying = true;
            MusicItem item = musicItems.get(index);
            mediaPlayer.setDataSource(this, item.uri);
            mediaPlayer.prepareAsync();
            
            String displayName = item.name;
            if (displayName.contains(".")) {
                displayName = displayName.substring(0, displayName.lastIndexOf("."));
            }
            songTitle.setText(displayName);
            
            musicSeekbar.setProgress(0);
            currentTime.setText("0:00");
            
        } catch (Exception e) {
            Toast.makeText(this, "Lecture impossible: " + musicItems.get(index).name, Toast.LENGTH_LONG).show();
            e.printStackTrace();
        }
    }

    private void filterMusicList(String query) {
        musicItems.clear();
        if (query.isEmpty()) {
            musicItems.addAll(allMusicItems);
        } else {
            for (MusicItem item : allMusicItems) {
                if (item.name.toLowerCase(Locale.getDefault()).contains(query.toLowerCase(Locale.getDefault()))) {
                    musicItems.add(item);
                }
            }
        }
        musicAdapter.submitList(new ArrayList<>(musicItems));
    }

    private static class MusicAdapter extends ListAdapter<MusicItem, MusicAdapter.MusicViewHolder> {
        private OnItemClickListener onItemClickListener;

        protected MusicAdapter() {
            super(new DiffUtil.ItemCallback<MusicItem>() {
                @Override
                public boolean areItemsTheSame(@NonNull MusicItem oldItem, @NonNull MusicItem newItem) {
                    return oldItem.uri.equals(newItem.uri);
                }

                @Override
                public boolean areContentsTheSame(@NonNull MusicItem oldItem, @NonNull MusicItem newItem) {
                    return oldItem.name.equals(newItem.name);
                }
            });
        }

        @NonNull
        @Override
        public MusicViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(android.R.layout.simple_list_item_1, parent, false);
            return new MusicViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull MusicViewHolder holder, int position) {
            holder.bind(getItem(position));
        }

        public void setOnItemClickListener(OnItemClickListener listener) {
            this.onItemClickListener = listener;
        }

        class MusicViewHolder extends RecyclerView.ViewHolder {
            private final TextView textView;

            public MusicViewHolder(@NonNull View itemView) {
                super(itemView);
                textView = itemView.findViewById(android.R.id.text1);
                itemView.setOnClickListener(v -> {
                    if (onItemClickListener != null) {
                        int pos = getAdapterPosition();
                        if (pos != RecyclerView.NO_POSITION) {
                            onItemClickListener.onItemClick(pos);
                        }
                    }
                });
            }

            public void bind(MusicItem item) {
                String name = item.name;
                if (name.contains(".")) {
                    name = name.substring(0, name.lastIndexOf("."));
                }
                textView.setText(name);
            }
        }

        interface OnItemClickListener {
            void onItemClick(int position);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }
            mediaPlayer.release();
            mediaPlayer = null;
        }
        handler.removeCallbacksAndMessages(null);
    }
}
