package com.pulse9.pulse9dev4.fastneuraltransfer;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Environment;
import android.provider.MediaStore;
import android.support.v4.content.FileProvider;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

public class MenuActivity extends AppCompatActivity {
    String mCurrentPhotoPath;
    private String mPhotoUri = "";
    static final int REQUEST_TAKE_PHOTO = 1;
    static final int REQUEST_PIC_PHOTO = 2;
    Animation clickanimation;

    private ImageView [] imageViews = new ImageView[4];
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_menu);
        clickanimation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.clickanimation);
        //////Firebase Crashlytics 강제 비정상종료
//        Button crashButton = new Button(this);
//        crashButton.setText("Crash!");
//        crashButton.setOnClickListener(new View.OnClickListener() {
//            public void onClick(View view) {
//                Crashlytics.getInstance().crash(); // Force a crash
//            }
//        });
//        addContentView(crashButton,
//                new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
//                        ViewGroup.LayoutParams.WRAP_CONTENT));
        //////
        initCardviews();
    }

    public void initCardviews()                  //버튼4개 init , set listener
    {

        imageViews[0] = (ImageView)findViewById(R.id.cam_but);           //카메라로 촬영 버튼
        imageViews[0].setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                v.startAnimation(clickanimation);
                MoveToCam();
            }
        });

        imageViews[1] = (ImageView)findViewById(R.id.gal_but);           //앨범사진 선택 버튼
        imageViews[1].setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                v.startAnimation(clickanimation);
                MoveToGal();
            }
        });

        imageViews[2] = (ImageView)findViewById(R.id.blog_but);          //블로그로 이동 버튼
        imageViews[2].setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                v.startAnimation(clickanimation);
                MoveToBlog();
            }
        });

        imageViews[3] = (ImageView)findViewById(R.id.insta_but);        //인스타로 이동버튼
        imageViews[3].setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View v) {
                v.startAnimation(clickanimation);
                MoveToInsta();
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {                             // 촬영한 사진이나 앨범의 URI를 다음화면으로 넘김(intent)
            if (requestCode == REQUEST_TAKE_PHOTO) {

                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("M_PHOTO_URI", mPhotoUri);
                startActivity(intent);
            }
            else if (requestCode == REQUEST_PIC_PHOTO)
            {
                mPhotoUri = data.getData().toString();
                Intent intent = new Intent(this, MainActivity.class);
                intent.putExtra("M_PHOTO_URI", mPhotoUri);
                startActivity(intent);
            }
        }
        else if (resultCode == RESULT_CANCELED) {
            Toast.makeText(this, "Picture was not taken", Toast.LENGTH_SHORT);
        }


    }


    private File createImageFile() throws IOException {              //촬영한 사진 앨범에 저장
        // Create an image file name
        String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        String imageFileName = "JPEG_" + timeStamp + "_";
        File storageDir = getExternalFilesDir(Environment.DIRECTORY_PICTURES);
        File image = File.createTempFile(
                imageFileName,  /* prefix */
                ".jpg",         /* suffix */
                storageDir      /* directory */
        );

        // Save a file: path for use with ACTION_VIEW intents
        mCurrentPhotoPath = image.getAbsolutePath();
        galleryAddPic();
        return image;
    }

    private void MoveToCam() {
        mPhotoUri = "";
        Intent takePictureIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);       //기기의 카메라 화면으로 이동
        // Ensure that there's a camera activity to handle the intent
        if (takePictureIntent.resolveActivity(getPackageManager()) != null) {
            // Create the File where the photo should go
            File photoFile = null;
            try {
                photoFile = createImageFile();
            } catch (IOException ex) {
                // Error occurred while creating the File
            }
            // Continue only if the File was successfully created
            if (photoFile != null) {
                Uri photoURI = FileProvider.getUriForFile(this,
                        "com.pulse9.pulse9dev4.fastneuraltransfer",
                        photoFile);
                takePictureIntent.putExtra(MediaStore.EXTRA_OUTPUT, photoURI);                   //사진의 명칭 지정
                mPhotoUri=photoURI.toString();

                startActivityForResult(takePictureIntent, REQUEST_TAKE_PHOTO);

            }
        }
    }
    private void galleryAddPic() {
        Intent mediaScanIntent = new Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE);
        File f = new File(mCurrentPhotoPath);
        Uri contentUri = Uri.fromFile(f);
        mediaScanIntent.setData(contentUri);
        this.sendBroadcast(mediaScanIntent);
    }

    public void MoveToGal()
    {
        Intent pickImageIntent = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
        startActivityForResult(pickImageIntent, REQUEST_PIC_PHOTO);
    }
    public void MoveToInfo()
    {
        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);

    }
    public void MoveToInsta()
    {
        Uri uri = Uri.parse("https://instagram.com/_paintly_");
        Intent likeIng = new Intent(Intent.ACTION_VIEW, uri);

        likeIng.setPackage("com.instagram.android");

        try {
            startActivity(likeIng);
        } catch (ActivityNotFoundException e) {
            startActivity(new Intent(Intent.ACTION_VIEW,
                    Uri.parse("https://instagram.com/_paintly_")));
        }
    }

    public void MoveToBlog()
    {
        Uri uri = Uri.parse("http://pulse9.strikingly.com/apps");
        Intent intent = new Intent(Intent.ACTION_VIEW, uri);
        startActivity(intent);

    }

}

