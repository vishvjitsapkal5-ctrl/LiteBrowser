package com.nobi.litebrowser;

import android.app.Activity;
import android.graphics.Bitmap;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Bundle;
import android.util.Patterns;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.SslErrorHandler;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Toast;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final String SEARCH_URL = "https://www.google.com/search?q=";

    private WebView webView;
    private View homeView;
    private EditText addressBar;
    private ProgressBar progressBar;
    private Button reloadButton;
    private boolean loading = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        webView = findViewById(R.id.webView);
        homeView = findViewById(R.id.homeView);
        addressBar = findViewById(R.id.addressBar);
        progressBar = findViewById(R.id.progressBar);
        reloadButton = findViewById(R.id.reloadButton);

        setupWebView();
        setupControls();
        showHome();
    }

    private void setupWebView() {
        WebSettings s = webView.getSettings();
        s.setJavaScriptEnabled(true);          // needed by most websites
        s.setDomStorageEnabled(true);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        s.setSupportMultipleWindows(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        // Deliberately NO addJavascriptInterface(): web pages get no access to app code.

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String scheme = request.getUrl().getScheme();
                if ("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme)) {
                    return false; // let WebView load it
                }
                if (request.isForMainFrame()) {
                    toast("Blocked unsupported link type");
                }
                return true; // block everything else (file:, intent:, javascript:, etc.)
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon) {
                setLoading(true);
                if (url != null) {
                    addressBar.setText(url);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                setLoading(false);
                if (url != null) {
                    addressBar.setText(url);
                }
            }

            @Override
            public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
                handler.cancel(); // never proceed past certificate errors
                setLoading(false);
                toast("Connection blocked: this site's security certificate is not valid.");
            }

            @Override
            public void onReceivedError(WebView view, WebResourceRequest request,
                                        android.webkit.WebResourceError error) {
                if (request.isForMainFrame()) {
                    setLoading(false);
                    toast("Could not load page: " + error.getDescription());
                }
            }
        });

        webView.setWebChromeClient(new WebChromeClient() {
            @Override
            public void onProgressChanged(WebView view, int newProgress) {
                progressBar.setProgress(newProgress);
                if (newProgress >= 100) {
                    progressBar.setVisibility(View.GONE);
                }
            }
        });
    }

    private void setupControls() {
        addressBar.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null
                    && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || enter) {
                submitAddress();
                return true;
            }
            return false;
        });

        findViewById(R.id.goButton).setOnClickListener(v -> submitAddress());
        findViewById(R.id.backButton).setOnClickListener(v -> goBackOrHome());
        findViewById(R.id.forwardButton).setOnClickListener(v -> goForward());
        findViewById(R.id.homeButton).setOnClickListener(v -> showHome());

        reloadButton.setOnClickListener(v -> {
            if (loading) {
                webView.stopLoading();
                setLoading(false);
            } else if (webView.getVisibility() == View.VISIBLE) {
                webView.reload();
            }
        });
    }

    private void submitAddress() {
        String url = buildUrl(addressBar.getText().toString());
        hideKeyboard();
        if (url == null) {
            return;
        }
        homeView.setVisibility(View.GONE);
        webView.setVisibility(View.VISIBLE);
        webView.loadUrl(url);
    }

    /** Turns user input into a URL: valid addresses open directly, anything else is a Google search. */
    private String buildUrl(String input) {
        String text = input == null ? "" : input.trim();
        if (text.isEmpty()) {
            return null;
        }
        String lower = text.toLowerCase(Locale.ROOT);
        boolean hasWebScheme = lower.startsWith("http://") || lower.startsWith("https://");

        if (!text.contains(" ") && Patterns.WEB_URL.matcher(text).matches()) {
            return hasWebScheme ? text : "https://" + text;
        }
        return SEARCH_URL + Uri.encode(text);
    }

    private void showHome() {
        webView.setVisibility(View.GONE);
        homeView.setVisibility(View.VISIBLE);
        progressBar.setVisibility(View.GONE);
        addressBar.setText("");
        hideKeyboard();
    }

    private void goBackOrHome() {
        if (webView.getVisibility() == View.VISIBLE) {
            if (webView.canGoBack()) {
                webView.goBack();
            } else {
                showHome();
            }
        }
    }

    private void goForward() {
        if (webView.getVisibility() != View.VISIBLE) {
            // Returning from the home screen to the page we left
            if (webView.getUrl() != null) {
                homeView.setVisibility(View.GONE);
                webView.setVisibility(View.VISIBLE);
                addressBar.setText(webView.getUrl());
            }
        } else if (webView.canGoForward()) {
            webView.goForward();
        }
    }

    private void setLoading(boolean isLoading) {
        loading = isLoading;
        reloadButton.setText(isLoading ? "Stop" : "Reload");
        if (isLoading) {
            progressBar.setProgress(0);
            progressBar.setVisibility(View.VISIBLE);
        } else {
            progressBar.setVisibility(View.GONE);
        }
    }

    private void hideKeyboard() {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) {
            imm.hideSoftInputFromWindow(addressBar.getWindowToken(), 0);
        }
        addressBar.clearFocus();
    }

    private void toast(String message) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    @Override
    public void onBackPressed() {
        if (webView.getVisibility() == View.VISIBLE) {
            goBackOrHome();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        webView.onResume();
    }

    @Override
    protected void onPause() {
        webView.onPause();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            ViewGroup parent = (ViewGroup) webView.getParent();
            if (parent != null) {
                parent.removeView(webView);
            }
            webView.stopLoading();
            webView.setWebChromeClient(null);
            webView.setWebViewClient(new WebViewClient());
            webView.removeAllViews();
            webView.destroy();
            webView = null;
        }
        super.onDestroy();
    }
                               }
