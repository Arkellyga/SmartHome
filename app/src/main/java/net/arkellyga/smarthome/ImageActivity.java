package net.arkellyga.smarthome;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.os.PersistableBundle;
import android.preference.PreferenceManager;
import android.support.annotation.Nullable;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;

public class ImageActivity extends AppCompatActivity {
    private static final int GALLERY_REQUEST = 1;
    private String mCurrentButton;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d("ImageActivity","onCreate");
        mCurrentButton = getIntent().getStringExtra("button");
        Intent photoPickerIntent = new Intent(Intent.ACTION_PICK);
        photoPickerIntent.setType("image/*");
        startActivityForResult(photoPickerIntent, GALLERY_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch (requestCode) {
            case GALLERY_REQUEST:
                Uri image = data.getData();
                if (image != null) {
                    Log.d("ImageActivity", image.toString());
                    Log.d("ImageActivity", image.getPath());
                    PreferenceManager.getDefaultSharedPreferences(this).edit()
                            .putString(mCurrentButton, image.toString()).apply();
                    finish();
                }
        }
    }
}
