package org.mewx.wenku8.activity;

import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.LinearGradient;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.util.TypedValue;
import android.view.KeyEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import com.afollestad.materialdialogs.GravityEnum;
import com.afollestad.materialdialogs.MaterialDialog;
import com.afollestad.materialdialogs.Theme;
import com.nostra13.universalimageloader.core.ImageLoader;
import com.umeng.analytics.MobclickAgent;

import org.apache.http.NameValuePair;
import org.mewx.wenku8.R;
import org.mewx.wenku8.component.ScrollViewNoFling;
import org.mewx.wenku8.global.GlobalConfig;
import org.mewx.wenku8.global.api.OldNovelContentParser;
import org.mewx.wenku8.global.api.VolumeList;
import org.mewx.wenku8.global.api.Wenku8API;
import org.mewx.wenku8.util.LightNetwork;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by MewX on 2015/6/6.
 */
public class VerticalReaderActivity extends AppCompatActivity {
    // constant
    private final String FromLocal = "fav";

    // private vars
    private String from = "";
    private int aid, vid, cid;
    private VolumeList volumeList= null;
    private MaterialDialog pDialog = null;
    private LinearLayout TextListLayout = null;
    private List<OldNovelContentParser.NovelContent> nc = null;
    private Typeface typeface;

    // Scroll runnable to last read position
    private Runnable runnableScroll = new Runnable() {
        @Override
        public void run() {
            ((ScrollViewNoFling) VerticalReaderActivity.this.findViewById(R.id.content_scrollview))
                    .scrollTo(0, GlobalConfig.getReadSavesRecord(cid,
                            TextListLayout.getMeasuredHeight()));
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.layout_vertical_reader_temp);

        // fetch values
        aid = getIntent().getIntExtra("aid", 1);
        volumeList = (VolumeList) getIntent().getSerializableExtra("volume");
        cid = getIntent().getIntExtra("cid",1);
        from = getIntent().getStringExtra("from");

        // get Novel Content
        typeface = Typeface.createFromAsset(getAssets(), "fonts/fzss-gbk.ttf");
        getNovelContent();

        // get view
        TextListLayout = (LinearLayout) VerticalReaderActivity.this
                .findViewById(R.id.novel_content_layout);
        Toast.makeText(this, getString(R.string.notice_volume_to_dark_mode), Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        MobclickAgent.onResume(this);

        // set navigation bar status, remember to disable "setNavigationBarTintEnabled"
        final int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        // This work only for android 4.4+
        if(Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            getWindow().getDecorView().setSystemUiVisibility(flags);

            // Code below is to handle presses of Volume up or Volume down.
            // Without this, after pressing volume buttons, the navigation bar will
            // show up and won't hide
            final View decorView = getWindow().getDecorView();
            decorView.setOnSystemUiVisibilityChangeListener(new View.OnSystemUiVisibilityChangeListener() {
                @Override
                public void onSystemUiVisibilityChange(int visibility) {
                    if((visibility & View.SYSTEM_UI_FLAG_FULLSCREEN) == 0) {
                        decorView.setSystemUiVisibility(flags);
                    }
                }
            });
        }
    }

    private void getNovelContent() {
        List<NameValuePair> targVar = new ArrayList<NameValuePair>();
        targVar.add(Wenku8API.getNovelContent(aid, cid, GlobalConfig.getCurrentLang()));

        final asyncNovelContentTask ast = new asyncNovelContentTask();
        ast.execute(targVar);

        pDialog = new MaterialDialog.Builder(this)
                .theme(Theme.LIGHT)
                .title(R.string.sorry_old_engine_preprocess)
                .content(R.string.sorry_old_engine_merging)
                .progress(false, 1, true)
                .cancelable(true)
                .cancelListener(new DialogInterface.OnCancelListener() {
                    @Override
                    public void onCancel(DialogInterface dialog) {
                        ast.cancel(true);
                        pDialog.dismiss();
                        pDialog = null;
                    }
                })
                .titleColor(R.color.default_text_color_black)
                .show();

        pDialog.setProgress(0);
        pDialog.setMaxProgress(1);
        pDialog.show();

        return;
    }

    class asyncNovelContentTask extends
            AsyncTask<List<NameValuePair>, Integer, Integer> {
        // fail return -1
        @Override
        protected Integer doInBackground(List<NameValuePair>... params) {

            try {
                String xml;
                if (from.equals(FromLocal))
                    xml = GlobalConfig.loadFullFileFromSaveFolder("novel", cid + ".xml");
                else {
                    byte[] tempXml = LightNetwork.LightHttpPost(
                            Wenku8API.getBaseURL(), params[0]);
                    if (tempXml == null)
                        return -100;
                    xml = new String(tempXml, "UTF-8");
                }

                nc = OldNovelContentParser.parseNovelContent(xml, pDialog);
                if (nc == null || nc.size() == 0) {
                    Log.e("MewX-Main",
                            "getNullFromParser (NovelContentParser.parseNovelContent(xml);)");

                    // network error or parse failed
                    return -100;
                }

                return 0;
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
            return -1;
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            return;
        }

        @Override
        protected void onPostExecute(Integer result) {
            if (result == -100) {
                    Toast.makeText(VerticalReaderActivity.this,
                            getResources().getString(R.string.system_network_error),
                            Toast.LENGTH_LONG).show();
                if (pDialog != null)
                    pDialog.dismiss();
                return;
            }

            // The abandoned way - dynamically adding textview into layout
            for (int i = 0; i < nc.size(); i++) {
                if (pDialog != null)
                    pDialog.setProgress(i);

                switch (nc.get(i).type) {
                    case 't':
                        TextView tempTV = new TextView(VerticalReaderActivity.this);
                        if (i == 0) {
                            tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                    GlobalConfig.getShowTextSize() + 6);
                            Shader shader = new LinearGradient(0, 0, 0,
                                    tempTV.getTextSize(), 0xFF003399, 0xFF6699FF,
                                    Shader.TileMode.CLAMP);
                            tempTV.getPaint().setShader(shader);
                        } else {
                            tempTV.setTextSize(TypedValue.COMPLEX_UNIT_SP,
                                    GlobalConfig.getShowTextSize());
                        }
                        tempTV.setText(nc.get(i).content);
                        tempTV.setLineSpacing(GlobalConfig.getShowTextSize() * 2.0f, 1.0f); // set line space
                        tempTV.setTypeface(typeface);
                        tempTV.setTextColor(getResources().getColor(R.color.reader_default_text_dark));
                        tempTV.setPadding(GlobalConfig.getShowTextPaddingLeft(),
                                GlobalConfig.getShowTextPaddingTop(),
                                GlobalConfig.getShowTextPaddingRight(), 0);
                        TextListLayout.addView(tempTV);
                        break;

                    case 'i':
                        final ImageView tempIV = new ImageView(VerticalReaderActivity.this);
                        tempIV.setClickable(true);
                        tempIV.setAdjustViewBounds(true);
                        tempIV.setScaleType(ImageView.ScaleType.FIT_CENTER);// CENTER_INSIDE
                        tempIV.setPadding(0, GlobalConfig.getShowTextPaddingTop(),
                                0, 0);
                        tempIV.setImageResource(R.drawable.ic_empty_image); // default

                        // async loader
                        final String imgFileName = GlobalConfig
                                .generateImageFileNameByURL(nc.get(i).content);
                        final String path = GlobalConfig
                                .getAvailableNovolContentImagePath(imgFileName);

                        if (path != null) {
                            ImageLoader.getInstance().displayImage(
                                    "file://" + path, tempIV);

                            tempIV.setOnClickListener(new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
//                                    Intent intent = new Intent();
//                                    intent.setClass(VerticalReaderActivity.this,
//                                            NovelImageActivity.class);
//                                    intent.putExtra("path", path);
//                                    startActivity(intent);
                                    Toast.makeText(VerticalReaderActivity.this, getResources().getString(R.string.sorry_old_engine_no_image_preview), Toast.LENGTH_SHORT).show();
//                                    VerticalReaderActivity.this.overridePendingTransition(
//                                            R.anim.in_from_right, R.anim.keep);
                                }
                            });
                        } else {
                            // define another asynctask to load image
                            // need to access local var - tempIV
                            class asyncDownloadImage extends
                                    AsyncTask<String, Integer, String> {
                                @Override
                                protected String doInBackground(String... params) {
                                    GlobalConfig.saveNovelContentImage(params[0]);
                                    String name = GlobalConfig
                                            .generateImageFileNameByURL(params[0]);
                                    return GlobalConfig
                                            .getAvailableNovolContentImagePath(name);
                                }

                                @Override
                                protected void onPostExecute(final String result) {
                                    ImageLoader.getInstance().displayImage(
                                            "file://" + result, tempIV);

                                    tempIV.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View v) {
//                                            Intent intent(intent); = new Intent();
//                                            intent.setClass(VerticalReaderActivity.this,
//                                                    NovelImageActivity.class);
//                                            intent.putExtra("path", result);
                                            Toast.makeText(VerticalReaderActivity.this, getResources().getString(R.string.sorry_old_engine_no_image_preview), Toast.LENGTH_SHORT).show();
//                                            startActivity
//                                            VerticalReaderActivity.this
//                                                    .overridePendingTransition(
//                                                            R.anim.in_from_right,
//                                                            R.anim.keep);
                                        }
                                    });

                                    return;
                                }

                            }
                            asyncDownloadImage async = new asyncDownloadImage();
                            async.execute(nc.get(i).content);
                        }

                        TextListLayout.addView(tempIV);
                        break;

                }
            }

            // end loading dialog
            if (pDialog != null)
                pDialog.dismiss();

            // show dialog
            if (GlobalConfig.getReadSavesRecord(cid,
                    TextListLayout.getMeasuredHeight()) > 100) {
                new MaterialDialog.Builder(VerticalReaderActivity.this)
                        .callback(new MaterialDialog.ButtonCallback() {
                            @Override
                            public void onPositive(MaterialDialog dialog) {
                                super.onPositive(dialog);
                                // set scroll view
                                Handler handler = new Handler();
                                handler.postDelayed(runnableScroll, 200);

                                Toast.makeText(VerticalReaderActivity.this, "Scroll to = "
                                                + GlobalConfig.getReadSavesRecord(cid, TextListLayout.getMeasuredHeight()),
                                        Toast.LENGTH_SHORT).show();
                            }
                        })
                        .theme(Theme.LIGHT)
                        .titleColor(R.color.default_text_color_black)
                        .backgroundColorRes(R.color.dlgBackgroundColor)
                        .contentColorRes(R.color.dlgContentColor)
                        .positiveColorRes(R.color.dlgPositiveButtonColor)
                        .negativeColorRes(R.color.dlgNegativeButtonColor)
                        .title(R.string.sorry_old_engine_notify)
                        .content(R.string.sorry_old_engine_jump)
                        .contentGravity(GravityEnum.CENTER)
                        .positiveText(R.string.dialog_positive_sure)
                        .negativeText(R.string.dialog_negative_biao)
                        .show();
            }

            return;
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        MobclickAgent.onPause(this);

        saveRecord();
        return;
    }

    private void saveRecord() {
        // cannot get height easily, except sum one by one
        GlobalConfig.addReadSavesRecord(cid,
                ((ScrollViewNoFling) VerticalReaderActivity.this
                        .findViewById(R.id.content_scrollview)).getScrollY(),
                TextListLayout.getMeasuredHeight());
        return;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (pDialog != null)
            pDialog.dismiss();
        pDialog = null;
        return;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_VOLUME_DOWN:
                TextListLayout.setBackgroundColor(getResources().getColor(R.color.reader_default_bg_black));

                // change text color
                for(int i = 1; i < TextListLayout.getChildCount(); i ++) {
                    View view = TextListLayout.getChildAt(i);
                    if(view instanceof TextView)
                        ((TextView)view).setTextColor(getResources().getColor(R.color.reader_default_text_light));
                }

                return true;
            case KeyEvent.KEYCODE_VOLUME_UP:
                TextListLayout.setBackgroundColor(getResources().getColor(R.color.reader_default_bg_yellow));

                // change text color
                for(int i = 1; i < TextListLayout.getChildCount(); i ++) {
                    View view = TextListLayout.getChildAt(i);
                    if(view instanceof TextView)
                        ((TextView)view).setTextColor(getResources().getColor(R.color.reader_default_text_dark));
                }

                return true;
        }
        return super.onKeyDown(keyCode, event);
    }
}
