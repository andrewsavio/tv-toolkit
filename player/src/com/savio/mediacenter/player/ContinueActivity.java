package com.savio.mediacenter.player;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** "Continue Watching": partly watched videos, newest first. OK plays (resumes), MENU/long-press is not needed. */
public class ContinueActivity extends Activity implements AdapterView.OnItemClickListener {
    private History history;
    private ArrayList<History.Entry> items = new ArrayList<History.Entry>();
    private ListView list;
    private TextView empty;
    private Adapter adapter;

    private int dp(int v) { return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()); }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        history = new History(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF0B1226);
        root.setPadding(dp(80), dp(48), dp(80), dp(32));
        TextView title = new TextView(this);
        title.setText("Continue Watching");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 34);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(0, 0, 0, dp(24));
        root.addView(title);
        list = new ListView(this);
        list.setDivider(new ColorDrawable(0x22FFFFFF));
        list.setDividerHeight(1);
        list.setSelector(new ColorDrawable(0x553A8DDE));
        list.setOnItemClickListener(this);
        adapter = new Adapter(this);
        list.setAdapter(adapter);
        root.addView(list, new LinearLayout.LayoutParams(-1, 0, 1f));
        empty = new TextView(this);
        empty.setText("Nothing to continue yet.\nVideos you stop part-way appear here.");
        empty.setTextColor(0xFF9FB3C8);
        empty.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        root.addView(empty);
        setContentView(root);
    }

    @Override
    protected void onResume() {
        super.onResume();
        items = history.recent(12);
        adapter.notifyDataSetChanged();
        empty.setVisibility(items.isEmpty() ? View.VISIBLE : View.GONE);
        list.setVisibility(items.isEmpty() ? View.GONE : View.VISIBLE);
        list.requestFocus();
    }

    @Override
    public void onItemClick(AdapterView<?> p, View v, int i, long id) {
        ArrayList<Uri> one = new ArrayList<Uri>();
        one.add(Uri.parse(items.get(i).path));
        Intent in = new Intent();
        in.setClassName(getPackageName(), PlayerActivity.class.getName());
        in.putParcelableArrayListExtra("playlist", one);
        in.putExtra("index", 0);
        startActivity(in);
    }

    private static String clock(long ms) {
        long s = Math.max(0, ms) / 1000;
        return s >= 3600 ? String.format(Locale.US, "%d:%02d:%02d", s / 3600, (s / 60) % 60, s % 60)
                : String.format(Locale.US, "%d:%02d", s / 60, s % 60);
    }

    static final class Adapter extends BaseAdapter {
        private final ContinueActivity a;
        Adapter(ContinueActivity a) { this.a = a; }
        public int getCount() { return a.items.size(); }
        public Object getItem(int i) { return a.items.get(i); }
        public long getItemId(int i) { return i; }

        public View getView(int i, View v, ViewGroup p) {
            LinearLayout box;
            TextView t, d;
            ProgressBar bar;
            if (v == null) {
                box = new LinearLayout(a);
                box.setOrientation(LinearLayout.VERTICAL);
                box.setPadding(a.dp(24), a.dp(16), a.dp(24), a.dp(16));
                t = new TextView(a);
                t.setTextColor(Color.WHITE);
                t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
                t.setSingleLine(true);
                d = new TextView(a);
                d.setTextColor(0xFF9FB3C8);
                d.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
                bar = new ProgressBar(a, null, android.R.attr.progressBarStyleHorizontal);
                bar.setMax(1000);
                box.addView(t);
                box.addView(d);
                box.addView(bar, new LinearLayout.LayoutParams(-1, a.dp(8)));
                box.setTag(new View[]{t, d, bar});
            } else {
                box = (LinearLayout) v;
                View[] tv = (View[]) box.getTag();
                t = (TextView) tv[0]; d = (TextView) tv[1]; bar = (ProgressBar) tv[2];
            }
            History.Entry e = a.items.get(i);
            t.setText(e.title == null ? e.path : e.title);
            d.setText(clock(e.pos) + " of " + clock(e.dur) + "  -  " + (e.dur > 0 ? Math.round((e.dur - e.pos) / 60000f) + " min left" : ""));
            bar.setProgress(e.dur > 0 ? (int) (e.pos * 1000 / e.dur) : 0);
            return box;
        }
    }
}
