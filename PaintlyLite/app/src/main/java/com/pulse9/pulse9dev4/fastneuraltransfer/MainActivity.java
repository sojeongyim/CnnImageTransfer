package com.pulse9.pulse9dev4.fastneuraltransfer;

import android.Manifest;
import android.app.AlertDialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Debug;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.provider.MediaStore;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Toast;

import org.tensorflow.contrib.android.TensorFlowInferenceInterface;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/*
    Number - Style name
    0 - 1-OIL-B
    1 - 1-LINE-B
    2 - 1-NEW-C
    3 - 1-LINE-A
    4 - 1-ACRYLIC-C
    5 - 1-ACRYLIC-A
    6 - 1-NEW-A
    7 - 1-NEW-B

 */
public class MainActivity extends AppCompatActivity implements Runnable, View.OnClickListener {

    //    private static final String INPUT_NODE = "batch_processing/Reshape_4";
    //    private static final String INPUT_NODE2 = "style_image_processing/batch";
    Animation clickanimation;     //버튼클릭 애니메이션
    private static final int NUM_STYLES = 8;     //필터 갯수
    private static final int NUM_OPTION = 4;     //back, rotate, save, original
    private static final String MODEL_FILE = "file:///android_asset/second_frozen.pb";
    private static final String INPUT_NODE = "input";
    private static final String INPUT_NODE2 = "style_num";
    private static final String OUTPUT_NODE = "transformer/expand/conv3/conv/Sigmoid";


    private static int WANTED_WIDTH = 600;
    private static int WANTED_HEIGHT = 400;

    final float[] styleVals = new float[NUM_STYLES];

    private ImageView mImageView;
    private ImageView mtransImageView;
    private ImageButton[] thumnailButtons = new ImageButton[NUM_STYLES];
    private ImageButton[] optionButtons = new ImageButton[NUM_OPTION];
    private ProgressBar pb;
    private SeekBar seekBar;
    private int rotateAngle=0;
    private int savePermission =0;

    private Bitmap moriginBitmap;
    private Bitmap mTransferredBitmap;
    private Uri mPhotoUri;
    String mPhoto;
    private TensorFlowInferenceInterface mInferenceInterface;
    private int Transparency = 255;   //투명도 조절(225: 완전불투명)
    private Bitmap resultBitmap;
    private static String[] PERMISSIONS_STORAGE = {
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
    };

    Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {          //image transfer된 후 호출
            String text = (String) msg.obj;
            Toast.makeText(MainActivity.this, text,
                    Toast.LENGTH_SHORT).show();


            mtransImageView.setImageBitmap(mTransferredBitmap);
            Transparency=255;
            seekBar.setProgress(Transparency);    //seekbar의 위치도 최대로 옮김
            mtransImageView.setAlpha(Transparency);         //변환된 사진을 완전 불투명하게하여 origin 이미지위에 올림

            seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {   //투명도 조절 바 이동시 listener
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
                public void onStartTrackingTouch(SeekBar seekBar) {
                }
                public void onProgressChanged(SeekBar seekBar, int progress,
                                              boolean fromUser) {
                    Transparency = progress;
                    mtransImageView.setAlpha(Transparency);
                }
            });
            for (int i = 0; i < thumnailButtons.length; i++) {         //비활성화시킨 버튼들을 다시 활성화 시킴
                thumnailButtons[i].setEnabled(true);
                thumnailButtons[i].setAlpha(255);
            }
            for (int i = 1; i < NUM_OPTION-1; i++) {
                optionButtons[i].setEnabled(true);
                optionButtons[i].setAlpha(255);
            }
            optionButtons[3].setVisibility(View.VISIBLE);
            for (int i = 0; i < NUM_STYLES; ++i) {
                styleVals[i] = 0.0f / NUM_STYLES;
            }
            pb.setVisibility(View.GONE);
            seekBar.setVisibility(View.VISIBLE);

        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        clickanimation = AnimationUtils.loadAnimation(getApplicationContext(), R.anim.clickanimation);

        for (int i = 0; i < NUM_STYLES; ++i) {
            styleVals[i] = 0.0f / NUM_STYLES;       //init Style Vales
        }

        mImageView = findViewById(R.id.imageview);
        mtransImageView = findViewById(R.id.overlapimageview);
        Intent intent = new Intent(this.getIntent());
        mPhoto = intent.getStringExtra("M_PHOTO_URI");
        mPhotoUri = Uri.parse(mPhoto);

        try {
            moriginBitmap =MediaStore.Images.Media.getBitmap(getContentResolver(), mPhotoUri);     //이미지 uri를 통해서 이미지 가져와 originbitmap에 저장
        } catch (IOException e) {
            Intent reIntent = new Intent(this, MenuActivity.class);
            startActivity(reIntent);
            e.printStackTrace();
        }

        if (mPhoto.indexOf("pulse9") != -1) {         //직접 촬영하여 가져온 이미지일경우 자동회전되므로 90도 rotate시켜 띄운다.
            mImageView.setRotation(90);
            mtransImageView.setRotation(90);
            rotateAngle +=90;
        }
        mImageView.setImageBitmap(moriginBitmap);
        initButtons();
    }

    public static Bitmap rotateBitmap(Bitmap bitmap, int orientation) {
        Matrix matrix = new Matrix();
        switch (orientation) {
            case 0:
                return bitmap;
            case 90:
                matrix.setRotate(90);
                break;
            case 180:
                matrix.setRotate(180);
                break;
            case 270:
                matrix.setRotate(-90);
                break;
            default:
                return bitmap;
        }
        try {
            Bitmap bmRotated = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
            bitmap=null;
            return bmRotated;
        }
        catch (OutOfMemoryError e) {
            e.printStackTrace();
            return null;
        }
    }

    public void run() {              //image transfer

//        Double available = new Double((Debug.getNativeHeapSize())/1024);
        Double free = new Double((Debug.getNativeHeapFreeSize())/1024);
//        Double allocated = new Double((Debug.getNativeHeapAllocatedSize())/1024);
//        Log.e("sojeong","debug.heap native: available " + available);
//        Log.e("sojeong","debug.heap native: free " + free);
//        Log.e("sojeong","debug.heap native: allocated " + allocated);
        WANTED_WIDTH= 600;
        WANTED_HEIGHT=400;

        if(free<160){
            WANTED_WIDTH= 400;
            WANTED_HEIGHT=250;
            if(free<130){
                WANTED_WIDTH= 300;
                WANTED_HEIGHT=200;
                if(free<100){
                    WANTED_WIDTH= 200;
                    WANTED_HEIGHT=170;
                }
            }
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();     //닫기
                }
            });
            alert.setMessage("The device capacity is too low.\n" +
                    "So the size of the photos will be reduced.");
            alert.show();
        }
        int[] intValues = new int[WANTED_WIDTH * WANTED_HEIGHT];
        float[] floatValues = new float[WANTED_WIDTH * WANTED_HEIGHT * 3];
        float[] outputValues = new float[WANTED_WIDTH * WANTED_HEIGHT * 3];

//        Bitmap bitmap = null;
//        try {
//            bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), mPhotoUri);
//
//        } catch (IOException e) {
//            Intent reIntent = new Intent(this, MenuActivity.class);
//            startActivity(reIntent);
//            e.printStackTrace();
//        }

        try {
        Bitmap scaledBitmap = Bitmap.createScaledBitmap(moriginBitmap, WANTED_WIDTH, WANTED_HEIGHT, true);
        scaledBitmap.getPixels(intValues, 0, scaledBitmap.getWidth(), 0, 0, scaledBitmap.getWidth(), scaledBitmap.getHeight());
        for (int i = 0; i < intValues.length; i++) {
            final int val = intValues[i];
            floatValues[i * 3] = ((val >> 16) & 0xFF);
            floatValues[i * 3 + 1] = ((val >> 8) & 0xFF);
            floatValues[i * 3 + 2] = (val & 0xFF);
        }
        AssetManager assetManager = getAssets();
        mInferenceInterface = new TensorFlowInferenceInterface(assetManager, MODEL_FILE);

        mInferenceInterface.feed(INPUT_NODE, floatValues, 1, WANTED_HEIGHT, WANTED_WIDTH, 3);
        mInferenceInterface.feed(INPUT_NODE2, styleVals, NUM_STYLES);
        mInferenceInterface.run(new String[]{OUTPUT_NODE}, false);
        mInferenceInterface.fetch(OUTPUT_NODE, outputValues);

        for (int i = 0; i < intValues.length; ++i) {
            intValues[i] = 0xFF000000
                    | (((int) (outputValues[i * 3] * 255)) << 16)
                    | (((int) (outputValues[i * 3 + 1] * 255)) << 8)
                    | ((int) (outputValues[i * 3 + 2] * 255));
        }

        Bitmap outputBitmap = scaledBitmap.copy(scaledBitmap.getConfig(), true);
        scaledBitmap=null;
        outputBitmap.setPixels(intValues, 0, outputBitmap.getWidth(), 0, 0, outputBitmap.getWidth(), outputBitmap.getHeight());

//        Double available2 = new Double((Debug.getNativeHeapSize())/1024);
//        Double free2 = new Double((Debug.getNativeHeapFreeSize())/1024);
//        Double allocated2 = new Double((Debug.getNativeHeapAllocatedSize())/1024);
//        Log.e("sojeong","debug.heap native2: available " + available2);
//        Log.e("sojeong","debug.heap native2: free " + free2);
//        Log.e("sojeong","debug.heap native2: allocated " + allocated2);

            mTransferredBitmap = Bitmap.createScaledBitmap(outputBitmap, moriginBitmap.getWidth(), moriginBitmap.getHeight(), true);
            outputBitmap=null;
        }catch (OutOfMemoryError e){
            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
            alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.dismiss();     //닫기
                }
            });
            alert.setMessage("The device capacity is still too low to convert the photos.");
            alert.show();
            Intent reIntent = new Intent(this, MenuActivity.class);
            startActivity(reIntent);
            e.printStackTrace();
        }
//        bitmap.recycle();
        Message msg = new Message();
        msg.obj = "Style Transfering";
        mHandler.sendMessage(msg);

    }

    private void initButtons() {
        optionButtons[0] = findViewById(R.id.back_butt);
        optionButtons[1] = findViewById(R.id.save_butt);
        optionButtons[2] = findViewById(R.id.origin_butt);
        optionButtons[3] = findViewById(R.id.rotate_butt);
        thumnailButtons[0] = findViewById(R.id.button0);
        thumnailButtons[1] = findViewById(R.id.button1);
        thumnailButtons[2] = findViewById(R.id.button2);
        thumnailButtons[3] = findViewById(R.id.button3);
        thumnailButtons[4] = findViewById(R.id.button4);
        thumnailButtons[5] = findViewById(R.id.button5);
        thumnailButtons[6] = findViewById(R.id.button6);
        thumnailButtons[7] = findViewById(R.id.button7);

        for (int i = 0; i < NUM_OPTION; i++) {
            optionButtons[i].setOnClickListener(this);
        }
        for (int i = 0; i < NUM_STYLES; i++) {
            thumnailButtons[i].setOnClickListener(this);
        }

        pb = findViewById(R.id.progressBar);
        seekBar = findViewById(R.id.Seekbar);

        seekBar.setVisibility(View.INVISIBLE);
        pb.setVisibility(View.INVISIBLE);
    }

    @Override
    public void onClick(View v) {

        int j = -1;
        switch (v.getId()) {

            case R.id.back_butt:
                v.startAnimation(clickanimation);
                finish();
                j = -1;
                break;

            case R.id.save_butt:    //save버튼 클릭시 외부저장소 접근 권한을 체크
                v.startAnimation(clickanimation);

                if ((ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.READ_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) ||
                        (ActivityCompat.checkSelfPermission(MainActivity.this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED)) {

                    if (ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.READ_EXTERNAL_STORAGE) ||
                            ActivityCompat.shouldShowRequestPermissionRationale(this,Manifest.permission.WRITE_EXTERNAL_STORAGE)){
                        //사용자가 다시 보지 않기에 체크를 하지 않고, 권한 설정을 거절한 이력이 있는 경우
                    } else{
                        if(savePermission !=0) {
                            //사용자가 다시 보지 않기에 체크하고, 권한 설정을 거절한 이력이 있는 경우
                            AlertDialog.Builder alert = new AlertDialog.Builder(MainActivity.this);
                            alert.setPositiveButton("OK", new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    dialog.dismiss();     //닫기
                                }
                            });
                            alert.setMessage("You can not save the photos because you denied permission.\n" +
                                    "If you want to save, \n" +
                                    "Go to [Settings - Application Manager - App Permissions].");
                            alert.show();
                        }
                        savePermission++; //case를 구분하기 위한 변수 : 1. save버튼을 처음 눌러서 권한요청창을 처음 보는 경우 2. '다시보지않기'버튼을 누른 후 권한 거절한 상태로 save버튼을 눌렀을 경우
                        //다음번에 1로 표시하여 두번째임을 알림
                    }
                    ActivityCompat.requestPermissions(MainActivity.this, PERMISSIONS_STORAGE, 1);
                }else {

                    while (rotateAngle >= 360) {
                        rotateAngle -= 360;      //rotate angle값을 360도 이하로 회전각도를 계산
                    }
                    if (mTransferredBitmap != null) {
                        resultBitmap = combineBitmap(moriginBitmap, mTransferredBitmap, Transparency); //transfer된 이미지가 존재할 경우 병합된 비트맵으로 저장
                    } else {
                        resultBitmap = moriginBitmap;            //origin이미지만 존재할 경우 origin 이미지만 저장
                    }
                    moriginBitmap = null;
                    mTransferredBitmap = null;
                    SaveToGallery saveToGallery = new SaveToGallery();    //저장을 위한 thread호출
                    saveToGallery.execute();
                }
                j = -1;
                break;


            case R.id.rotate_butt:                     //view를 회전하면서 rotate angle에 값 저장
                v.startAnimation(clickanimation);
                rotateAngle +=90;
                mImageView.setRotation(rotateAngle);
                mtransImageView.setRotation(rotateAngle);
                j = -1;
                break;

            case R.id.origin_butt:
                v.startAnimation(clickanimation);
                showOriginImage();
                j = -1;
                break;

            case R.id.button0:
                v.startAnimation(clickanimation);
                j = 0;
                break;
            case R.id.button1:
                v.startAnimation(clickanimation);
                j = 1;
                break;
            case R.id.button2:
                v.startAnimation(clickanimation);
                j = 2;
                break;
            case R.id.button3:
                v.startAnimation(clickanimation);
                j = 3;
                break;
            case R.id.button4:
                v.startAnimation(clickanimation);
                j = 4;
                break;
            case R.id.button5:
                v.startAnimation(clickanimation);
                j = 5;
                break;
            case R.id.button6:
                v.startAnimation(clickanimation);
                j = 6;
                break;
            case R.id.button7:
                v.startAnimation(clickanimation);
                j = 7;
                break;
            default:
                break;

        }
        if (j != -1) {
            startThread(j);          //j값에 따라 필터를 달리하여 thread로 transfer진행
        }
    }

    private Bitmap combineBitmap(Bitmap originBmp, Bitmap transferredBmp, int Transparency) {  //두개의 비트맵을 하나의 비트맵으로 합침
        Bitmap BothBitmap = Bitmap.createBitmap(transferredBmp.getWidth()
                , transferredBmp.getHeight()
                , transferredBmp.getConfig());
        Paint alphaPaint = new Paint();
        alphaPaint.setAlpha(Transparency);

        Canvas canvas = new Canvas(BothBitmap);
        canvas.drawBitmap(originBmp, new Matrix(), null);
        canvas.drawBitmap(transferredBmp, new Matrix(), alphaPaint);
        originBmp=null;
        transferredBmp=null;

        return BothBitmap;

    }

    private void startThread(int k) {
        if (k != -1) {
            mTransferredBitmap = null;

            pb.setVisibility(View.VISIBLE);
            styleVals[k] = 1f;                      //변형하려는 필터의 배열위치에 1저장
            Thread thread = new Thread(MainActivity.this);
            thread.start();               //thread시작
            for (int i = 0; i < NUM_STYLES; i++) {
                thumnailButtons[i].setEnabled(false);
                thumnailButtons[i].setAlpha(100);
            }
            for (int i = 1; i < NUM_OPTION-1; i++) {
                optionButtons[i].setEnabled(false);
                optionButtons[i].setAlpha(100);
            }
            optionButtons[3].setVisibility(View.INVISIBLE);
            seekBar.setVisibility(View.INVISIBLE);            //변형되는 동안 다른버튼 비활성화, 투명하게 조정
        }
    }

    private void showOriginImage() {               //original버튼 눌렀을 때 transfer 이미지 투명하게 하고 transferred bitmap null로 지정
        seekBar.setVisibility(View.INVISIBLE);
        mtransImageView.setAlpha(0);
        mTransferredBitmap =null;
    }

    private class SaveToGallery extends AsyncTask<String, Void, String> {          //저장을 위한 thread class
        ProgressDialog progressDialog;

        @Override

        protected void onPreExecute() {                 //전처리

            super.onPreExecute();

            progressDialog = ProgressDialog.show(MainActivity.this,

                    "Please Wait", null, true, true);          //로딩 바

            for (int i = 0; i <  NUM_STYLES; i++) {
                thumnailButtons[i].setEnabled(false);
                thumnailButtons[i].setAlpha(100);
            }
            for (int i = 1; i <  NUM_OPTION; i++) {
                optionButtons[i].setEnabled(false);
                optionButtons[i].setAlpha(100);
            }
            seekBar.setVisibility(View.INVISIBLE);
            optionButtons[3].setVisibility(View.INVISIBLE);
            resultBitmap=rotateBitmap(resultBitmap,rotateAngle);            //버튼 비활성화와 bitmap 사용자 지정 각도로 rotate
        }


        @Override

        protected void onPostExecute(String result) {            //후처리

            super.onPostExecute(result);
            progressDialog.dismiss();
            resultBitmap=null;
            Toast.makeText(MainActivity.this, "저장을 완료했습니다.", Toast.LENGTH_SHORT).show();
//            Intent reIntent = new Intent(MainActivity.this, MenuActivity.class);
//            startActivity(reIntent);
            finish();

//            for (int i = 0; i < NUM_STYLES; i++) {
//                thumnailButtons[i].setEnabled(true);
//                thumnailButtons[i].setAlpha(255);
//            }
//            for (int i = 1; i <  NUM_OPTION; i++) {
//                optionButtons[i].setEnabled(true);
//                optionButtons[i].setAlpha(255);
//            }
        }

        @Override
        protected String doInBackground(String... params) {
            String timeStamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String root = Environment.getExternalStorageDirectory().toString();
            File myDir = new File(root+"/pictures/Myapp");
            myDir.mkdirs();
            String fname = "IMAGE_" + timeStamp + ".jpg";
            File file = new File(myDir, fname);                              //날짜 시간으로 파일 만들어서 갤러리경로로 비트맵 저장
//        if (file.exists()) file.delete();
            try {
                FileOutputStream out = new FileOutputStream(file);
                resultBitmap.compress(Bitmap.CompressFormat.JPEG, 90, out);
                out.flush();
                out.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
            MainActivity.this.sendBroadcast(new Intent( Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)) );
            return "";
        }
    }
}
