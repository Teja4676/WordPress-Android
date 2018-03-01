package org.wordpress.android.ui;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;

import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;
import org.wordpress.android.R;
import org.wordpress.android.WordPress;
import org.wordpress.android.analytics.AnalyticsTracker.Stat;
import org.wordpress.android.fluxc.Dispatcher;
import org.wordpress.android.fluxc.generated.SiteActionBuilder;
import org.wordpress.android.fluxc.model.SiteModel;
import org.wordpress.android.fluxc.store.AccountStore;
import org.wordpress.android.fluxc.store.SiteStore;
import org.wordpress.android.login.LoginMode;
import org.wordpress.android.ui.JetpackConnectionWebViewActivity.Source;
import org.wordpress.android.ui.accounts.LoginActivity;
import org.wordpress.android.util.AnalyticsUtils;
import org.wordpress.android.util.AppLog;
import org.wordpress.android.util.ToastUtils;

import javax.inject.Inject;

import static org.wordpress.android.WordPress.SITE;
import static org.wordpress.android.ui.JetpackUtils.trackWithSource;
import static org.wordpress.android.ui.RequestCodes.JETPACK_LOGIN;

/**
 * An activity to handle Jetpack deeplink
 * <p>
 * wordpress://jetpack-connection?reason={error}
 * <p>
 * Redirects users to the stats activity if the jetpack connection was succesful
 */
public class JetpackConnectionDeeplinkActivity extends AppCompatActivity {
    private String reason;
    private Source source;

    @Inject
    AccountStore mAccountStore;
    @Inject
    Dispatcher mDispatcher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        ((WordPress) getApplication()).component().inject(this);

        setContentView(R.layout.stats_loading_activity);


        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setElevation(0);
            actionBar.setTitle(R.string.stats);
            actionBar.setDisplayShowTitleEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        String action = getIntent().getAction();
        Uri uri = getIntent().getData();

        AnalyticsUtils.trackWithDeepLinkData(Stat.DEEP_LINKED, action, uri);

        // check if this intent is started via custom scheme link
        if (Intent.ACTION_VIEW.equals(action) && uri != null) {

            //Non-empty reason does not mean we're not connected to Jetpack
            //- one of the errors is "already-connected"
            reason = uri.getQueryParameter("reason");
            source = Source.fromString(uri.getQueryParameter("source"));
            if (mAccountStore.hasAccessToken()) {
                // if user is signed in wpcom show the stats or notifications right away
                trackResultAndFinish();
            } else {
                // An edgecase when the user is logged out in the app but logged in in webview
                Intent loginIntent = new Intent(this, LoginActivity.class);
                LoginMode.JETPACK_STATS.putInto(loginIntent);
                this.startActivityForResult(loginIntent, JETPACK_LOGIN);
            }
        } else {
            finish();
        }
    }

    private void trackResultAndFinish() {
        if (!TextUtils.isEmpty(reason)) {
            AppLog.e(AppLog.T.API, "Could not connect to Jetpack, reason: "+reason);
            ToastUtils.showToast(this, reason);
        } else {
            trackWithSource(Stat.SIGNED_INTO_JETPACK, source);
        }
        if (source == Source.STATS) {
            //Reload the site to get new stats
            reloadSite();
        } else {
            //In case of coming from anywhere else, we finish the activity
            finish();
        }
    }

    @Override
    protected void onStop() {
        mDispatcher.unregister(this);
        super.onStop();
    }

    @Override
    protected void onStart() {
        super.onStart();
        mDispatcher.register(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == RequestCodes.JETPACK_LOGIN && resultCode == RESULT_OK) {
            trackResultAndFinish();
        } else {
            finishAndGoBackToSource();
        }
    }

    private void reloadSite() {
        //Reloads the site
        SiteModel site = (SiteModel) getIntent().getSerializableExtra(SITE);
        mDispatcher.dispatch(SiteActionBuilder.newFetchSiteAction(site));
    }

    @Override
    public void onBackPressed() {
        super.onBackPressed();
        finishAndGoBackToSource();
    }

    @SuppressWarnings("unused")
    @Subscribe(threadMode = ThreadMode.MAIN)
    public void onSiteChanged(SiteStore.OnSiteChanged event) {
        finishAndGoBackToSource();
    }

    private void finishAndGoBackToSource() {
        if (source != null && source == Source.STATS) {
            SiteModel site = (SiteModel) getIntent().getSerializableExtra(SITE);
            ActivityLauncher.viewBlogStats(this, site);
        }
        finish();
    }
}
