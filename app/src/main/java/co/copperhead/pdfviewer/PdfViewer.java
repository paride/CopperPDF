package co.copperhead.pdfviewer;

import android.annotation.SuppressLint;
import android.app.ActionBar;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.print.PrintAttributes;
import android.print.PrintDocumentAdapter;
import android.print.PrintManager;
import android.provider.MediaStore;
import android.provider.OpenableColumns;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.NumberPicker;
import android.widget.Toast;

public class PdfViewer extends Activity {
    private static final int MAX_ZOOM_LEVEL = 4;
    private static final int ACTION_OPEN_DOCUMENT_REQUEST_CODE = 1;
    private static final String STATE_URI = "uri";
    private static final String STATE_PAGE = "page";
    private static final String STATE_ZOOM_LEVEL = "zoomLevel";

    private WebView mWebView;
    private Uri mUri;
    private Channel mChannel;
    private boolean documentLoaded;

    private class Channel {
        private int mPage;
        private int mNumPages;
        private int mZoomLevel;

        private Channel() {
            mZoomLevel = 2;
        }

        @JavascriptInterface
        public String getUrl() {
            return mUri.toString();
        }

        @JavascriptInterface
        public int getPage() {
            return mPage;
        }

        @JavascriptInterface
        public int getZoomLevel() {
            return mZoomLevel;
        }

        @JavascriptInterface
        public void setNumPages(int numPages) {
            mNumPages = numPages;
        }

        @JavascriptInterface
        public void onDocumentLoaded() { documentLoaded = true; invalidateOptionsMenu(); }
    }

    @Override
    @SuppressLint("SetJavaScriptEnabled")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);

        setContentView(R.layout.webview);
        ActionBar actionBar = getActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
            actionBar.setHomeActionContentDescription(R.string.action_close);
        }

        mWebView = findViewById(R.id.webView1);
        WebSettings settings = mWebView.getSettings();
        settings.setJavaScriptEnabled(true);

        settings.setAllowFileAccess(false);
        settings.setAllowUniversalAccessFromFileURLs(true);

        mChannel = new Channel();
        mWebView.addJavascriptInterface(mChannel, "channel");

        mWebView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                if (mUri != null) {
                    loadPdf();
                }
            }
        });

        // some apps launch the PDF document, but leave the keyboard open, so hide it
        View view = this.getCurrentFocus();
        if (view == null) {
            view = findViewById(android.R.id.content).getRootView();
        }
        InputMethodManager imm = (InputMethodManager)getSystemService(Context.INPUT_METHOD_SERVICE);
        imm.hideSoftInputFromWindow(view.getWindowToken(), 0);

        Intent intent = getIntent();
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_VIEW.equals(action)) {
            if (!"application/pdf".equals(type)) {
                String appName = getString(R.string.app_name);
                Toast.makeText(this,
                        appName + ": unsupported file type: " + intent.getType(),
                        Toast.LENGTH_SHORT).show();
                setResult(RESULT_CANCELED);
                finish();
                return;
            }
            mUri = intent.getData();
            mChannel.mPage = 1;
        }

        if (savedInstanceState != null) {
            mUri = savedInstanceState.getParcelable(STATE_URI);
            mChannel.mPage = savedInstanceState.getInt(STATE_PAGE);
            mChannel.mZoomLevel = savedInstanceState.getInt(STATE_ZOOM_LEVEL);
        }

        mWebView.loadUrl("file:///android_asset/viewer.html");
    }

    private void loadPdf() {
        mWebView.evaluateJavascript("onGetDocument()", null);

        ContentResolver contentResolver = getContentResolver();
        Cursor cursor = contentResolver.query(mUri, null, null, null, null);
        String[] projection = new String[]{
                OpenableColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.TITLE,
        };
        if (cursor != null) {
            if (cursor.moveToNext()) {
                for (String check : projection) {
                    int index = cursor.getColumnIndex(check);
                    if (index > -1) {
                        String name = cursor.getString(index);
                        if (!TextUtils.isEmpty(name)) {
                            setTitle(name);
                            setTaskDescription(new ActivityManager.TaskDescription(name));
                            break;
                        }
                    }
                }
            }
            cursor.close();
        }
    }

    private void createPrintJob(WebView webView) {
        PrintManager printManager = (PrintManager) this.getSystemService(Context.PRINT_SERVICE);
        // TODO: get PDF metadata (title) from PDF.js, replace deprecated method
        PrintDocumentAdapter adapter = webView.createPrintDocumentAdapter();
        printManager.print(getString(R.string.pdf_document), adapter, new PrintAttributes.Builder().build());
    }

    private void openDocument() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        startActivityForResult(intent, ACTION_OPEN_DOCUMENT_REQUEST_CODE);
    }

    private void renderDocument() {
        documentLoaded = false;
        mWebView.evaluateJavascript("onRenderPage()", null);
    }

    private void closeDocument() {
        if (mWebView != null) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    mWebView.loadUrl("about:blank");
                    if (Build.VERSION.SDK_INT < 21) {
                        finish();
                    } else {
                        finishAndRemoveTask();
                    }
                }
            });
        }
    }

    @Override
    public void onSaveInstanceState(Bundle savedInstanceState) {
        super.onSaveInstanceState(savedInstanceState);
        savedInstanceState.putParcelable(STATE_URI, mUri);
        savedInstanceState.putInt(STATE_PAGE, mChannel.mPage);
        savedInstanceState.putInt(STATE_ZOOM_LEVEL, mChannel.mZoomLevel);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent resultData) {
        if (requestCode == ACTION_OPEN_DOCUMENT_REQUEST_CODE && resultCode == Activity.RESULT_OK) {
            if (resultData != null) {
                mUri = resultData.getData();
                mChannel.mPage = 1;
                loadPdf();
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        super.onCreateOptionsMenu(menu);
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.pdf_viewer, menu);

        if (documentLoaded) {
            menu.findItem(R.id.action_print).setVisible(true);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_previous:
                if (mChannel.mPage > 1) {
                    mChannel.mPage--;
                    renderDocument();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_next:
                if (mChannel.mPage < mChannel.mNumPages) {
                    mChannel.mPage++;
                    renderDocument();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_open:
                openDocument();
                return super.onOptionsItemSelected(item);

            case android.R.id.home:
            case R.id.action_close:
                closeDocument();
                return true;

            case R.id.action_zoom_out:
                if (mChannel.mZoomLevel > 0) {
                    mChannel.mZoomLevel--;
                    renderDocument();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_zoom_in:
                if (mChannel.mZoomLevel < MAX_ZOOM_LEVEL) {
                    mChannel.mZoomLevel++;
                    renderDocument();
                }
                return super.onOptionsItemSelected(item);

            case R.id.action_jump_to_page: {
                final NumberPicker picker = new NumberPicker(this);
                picker.setMinValue(1);
                picker.setMaxValue(mChannel.mNumPages);
                picker.setValue(mChannel.mPage);

                final FrameLayout layout = new FrameLayout(this);
                layout.addView(picker, new FrameLayout.LayoutParams(
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT,
                    Gravity.CENTER));

                new AlertDialog.Builder(this)
                    .setView(layout)
                    .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                        @Override
                        public void onClick(DialogInterface dialogInterface, int i) {
                            int page = picker.getValue();
                            if (page >= 1 && page <= mChannel.mNumPages) {
                                mChannel.mPage = page;
                                renderDocument();
                            }
                        }
                    })
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();

                return super.onOptionsItemSelected(item);
            }
            case R.id.action_print:
                // TODO: should wait for PDF.js to have fully loaded page
                createPrintJob(mWebView);
                return super.onOptionsItemSelected(item);
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
