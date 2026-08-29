package com.example.vibrationmonitor;

import android.app.*;
import android.os.*;
import android.graphics.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.text.*;
import java.util.*;

public class HistoryActivity extends Activity {

    private double spec = 2.0;
    private File dataDir;
    private final ArrayList<EventStat> events = new ArrayList<>();

    static class EventStat {
        File file;
        ArrayList<Double> values = new ArrayList<>();
        double avg, max, min, over;
        String dateText;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        spec = getIntent().getDoubleExtra("spec", 2.0);
        dataDir = new File(getExternalFilesDir(null), "VibrationData");

        ScrollView scroll = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);

        int p = dp(16);
        root.setPadding(p, p, p, dp(100));

        TextView title = new TextView(this);
        title.setText("진동 이력 관리");
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 0, 0, dp(12));
        root.addView(title);

        TextView specText = new TextView(this);
        specText.setText(String.format(Locale.US,
                "현재 SPEC : %.2f m/s²", spec));
        specText.setTextSize(18);
        root.addView(specText);

        loadEvents();

        TextView count = new TextView(this);
        count.setText("저장 이벤트 : " + events.size() + "건");
        count.setTextSize(18);
        count.setPadding(0, dp(6), 0, dp(10));
        root.addView(count);

        if (events.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("\n저장된 CSV 이벤트가 없습니다.");
            empty.setTextSize(18);
            root.addView(empty);
        } else {
            TextView trendTitle = new TextView(this);
            trendTitle.setText("이벤트 최대 진동 Trend");
            trendTitle.setTextSize(20);
            trendTitle.setPadding(0, dp(10), 0, dp(4));
            root.addView(trendTitle);

            TrendView trend = new TrendView(this, events, spec);
            trend.setLayoutParams(new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, dp(260)));
            root.addView(trend);

            TextView listTitle = new TextView(this);
            listTitle.setText("\n최근 이벤트");
            listTitle.setTextSize(20);
            root.addView(listTitle);

            for (EventStat e : events) {
                Button b = new Button(this);

                b.setText(
                    e.dateText +
                    String.format(Locale.US,
                        "\nMAX %.3f   AVG %.3f   초과 +%.3f m/s²",
                        e.max, e.avg, e.over)
                );

                b.setAllCaps(false);
                b.setOnClickListener(v -> showDetail(e));
                root.addView(b);
            }
        }

        Button close = new Button(this);
        close.setText("돌아가기");
        close.setOnClickListener(v -> finish());
        root.addView(close);

        scroll.addView(root);
        setContentView(scroll);
    }

    private void loadEvents() {
        events.clear();

        File[] files = dataDir.listFiles((dir, name) ->
                name.toLowerCase(Locale.US).endsWith(".csv"));

        if (files == null) return;

        Arrays.sort(files,
                (a,b) -> Long.compare(b.lastModified(), a.lastModified()));

        for (File f : files) {
            EventStat e = readEvent(f);
            if (e != null) events.add(e);
        }
    }

    private EventStat readEvent(File f) {
        EventStat e = new EventStat();
        e.file = f;

        try (BufferedReader br =
                     new BufferedReader(new FileReader(f))) {

            String line;

            while ((line = br.readLine()) != null) {
                String[] a = line.trim().split(",");

                if (a.length < 2) continue;

                try {
                    e.values.add(
                            Double.parseDouble(a[1].trim()));
                } catch (Exception ignored) {}
            }

        } catch (Exception ex) {
            return null;
        }

        if (e.values.isEmpty()) return null;

        double sum = 0;
        e.max = -Double.MAX_VALUE;
        e.min = Double.MAX_VALUE;

        for (double v : e.values) {
            sum += v;
            if (v > e.max) e.max = v;
            if (v < e.min) e.min = v;
        }

        e.avg = sum / e.values.size();
        e.over = Math.max(0, e.max - spec);
        e.dateText = formatFileDate(f);

        return e;
    }

    private String formatFileDate(File f) {
        String n = f.getName();

        try {
            if (n.startsWith("event_") && n.length() >= 21) {
                String raw = n.substring(6, 21);

                Date d = new SimpleDateFormat(
                        "yyyyMMdd_HHmmss", Locale.US).parse(raw);

                return new SimpleDateFormat(
                        "yyyy-MM-dd HH:mm:ss", Locale.US).format(d);
            }
        } catch (Exception ignored) {}

        return n;
    }

    private void showDetail(EventStat e) {

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(12), dp(12), dp(12), dp(12));

        TextView info = new TextView(this);
        info.setText(
                e.dateText +
                "\n파일 : " + e.file.getName() +
                "\n데이터 수 : " + e.values.size() + "개" +
                String.format(Locale.US,
                        "\n평균값 : %.3f m/s²", e.avg) +
                String.format(Locale.US,
                        "\n최대값 : %.3f m/s²", e.max) +
                String.format(Locale.US,
                        "\n최소값 : %.3f m/s²", e.min) +
                String.format(Locale.US,
                        "\n현재 SPEC : %.2f m/s²", spec) +
                String.format(Locale.US,
                        "\nSPEC 초과량 : %.3f m/s²", e.over)
        );

        info.setTextSize(16);
        box.addView(info);

        DetailView graph =
                new DetailView(this, e.values, spec);

        graph.setLayoutParams(
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        dp(300)));

        box.addView(graph);

        new AlertDialog.Builder(this)
                .setTitle("이벤트 상세 그래프")
                .setView(box)
                .setPositiveButton("닫기", null)
                .show();
    }

    private int dp(int v) {
        return (int)(v *
                getResources().getDisplayMetrics().density);
    }

    static class DetailView extends View {

        private final ArrayList<Double> values;
        private final double spec;

        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);

        DetailView(android.content.Context c,
                   ArrayList<Double> values,
                   double spec) {
            super(c);

            this.values = values;
            this.spec = spec;

            line.setColor(Color.rgb(20,110,200));
            line.setStrokeWidth(3f);
            line.setStyle(Paint.Style.STROKE);

            specPaint.setColor(Color.RED);
            specPaint.setStrokeWidth(3f);

            grid.setColor(Color.LTGRAY);
            grid.setStrokeWidth(1f);

            text.setColor(Color.DKGRAY);
            text.setTextSize(28f);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            if (values.isEmpty()) return;

            float w = getWidth();
            float h = getHeight();

            double max = spec;

            for (double v : values)
                if (v > max) max = v;

            max *= 1.15;
            if (max <= 0) max = 1;

            for (int i=1; i<5; i++) {
                float y = h * i / 5f;
                c.drawLine(0,y,w,y,grid);
            }

            float sy = (float)(h - spec/max*h);
            c.drawLine(0,sy,w,sy,specPaint);
            c.drawText(
                    String.format(Locale.US,
                            "SPEC %.2f", spec),
                    8, Math.max(30,sy-8), text);

            Path path = new Path();

            for (int i=0; i<values.size(); i++) {

                float x = values.size()==1
                        ? 0
                        : w*i/(values.size()-1f);

                float y = (float)(
                        h - values.get(i)/max*h);

                if (i==0) path.moveTo(x,y);
                else path.lineTo(x,y);
            }

            c.drawPath(path,line);
        }
    }

    static class TrendView extends View {

        private final ArrayList<EventStat> events;
        private final double spec;

        private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint specPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint point = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint grid = new Paint(Paint.ANTI_ALIAS_FLAG);

        TrendView(android.content.Context c,
                  ArrayList<EventStat> events,
                  double spec) {
            super(c);

            this.events = events;
            this.spec = spec;

            line.setColor(Color.rgb(20,110,200));
            line.setStrokeWidth(4f);
            line.setStyle(Paint.Style.STROKE);

            specPaint.setColor(Color.RED);
            specPaint.setStrokeWidth(3f);

            point.setColor(Color.rgb(20,110,200));

            grid.setColor(Color.LTGRAY);
            grid.setStrokeWidth(1f);
        }

        @Override
        protected void onDraw(Canvas c) {
            super.onDraw(c);

            if (events.isEmpty()) return;

            float w = getWidth();
            float h = getHeight();

            double scaleMax = spec;

            for (EventStat e : events)
                if (e.max > scaleMax) scaleMax = e.max;

            scaleMax *= 1.15;
            if (scaleMax <= 0) scaleMax = 1;

            for (int i=1; i<5; i++) {
                float y = h*i/5f;
                c.drawLine(0,y,w,y,grid);
            }

            float sy = (float)(h - spec/scaleMax*h);
            c.drawLine(0,sy,w,sy,specPaint);

            Path path = new Path();

            // 오래된 이벤트 → 최신 이벤트 순으로 그림
            for (int i=0; i<events.size(); i++) {

                EventStat e =
                        events.get(events.size()-1-i);

                float x = events.size()==1
                        ? w/2
                        : w*i/(events.size()-1f);

                float y = (float)(
                        h - e.max/scaleMax*h);

                if (i==0) path.moveTo(x,y);
                else path.lineTo(x,y);

                c.drawCircle(x,y,6f,point);
            }

            c.drawPath(path,line);
        }
    }
}
