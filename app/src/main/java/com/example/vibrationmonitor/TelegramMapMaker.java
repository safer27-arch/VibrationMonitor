package com.example.vibrationmonitor;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;

import java.io.File;
import java.io.FileOutputStream;

public class TelegramMapMaker {

    public static File create(
            File folder,
            String baseName,
            double lat,
            double lon
    ) {
        try {
            int w = 900;
            int h = 600;

            Bitmap bmp = Bitmap.createBitmap(
                    w, h, Bitmap.Config.ARGB_8888);

            Canvas c = new Canvas(bmp);
            c.drawColor(Color.WHITE);

            Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
            fill.setColor(Color.rgb(238,242,245));

            Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
            border.setColor(Color.DKGRAY);
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(6);

            Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
            text.setColor(Color.DKGRAY);
            text.setTextSize(38);

            Paint red = new Paint(Paint.ANTI_ALIAS_FLAG);
            red.setColor(Color.RED);
            red.setTextSize(30);

            float L=70, T=100, R=830, B=500;

            c.drawText("LGES WA5 Event Location",70,55,text);
            c.drawRect(L,T,R,B,fill);
            c.drawRect(L,T,R,B,border);
            c.drawText("WA5",L+15,T+45,text);

            double lat1=51.0245604;
            double lon1=16.8860644;
            double lat2=51.0239081;
            double lon2=16.8896636;
            double lat4=51.0236584;
            double lon4=16.8856524;

            double ref=(lat1+lat2+lat4)/3.0;
            double ml=111320.0;
            double mn=111320.0*Math.cos(Math.toRadians(ref));

            double bx=(lon-lon1)*mn;
            double by=(lat-lat1)*ml;

            double ax=(lon2-lon1)*mn;
            double ay=(lat2-lat1)*ml;

            double cx=(lon4-lon1)*mn;
            double cy=(lat4-lat1)*ml;

            double det=ax*cy-ay*cx;

            double u=0.5;
            double v=0.5;

            if(Math.abs(det)>0.000001){
                u=(bx*cy-by*cx)/det;
                v=(ax*by-ay*bx)/det;
            }

            boolean inside =
                    u>=0 && u<=1 &&
                    v>=0 && v<=1;

            u=Math.max(0,Math.min(1,u));
            v=Math.max(0,Math.min(1,v));

            float x=(float)(L+u*(R-L));
            float y=(float)(T+v*(B-T));

            c.drawCircle(x,y,18,red);
            c.drawText("Event",x+22,y-15,red);

            if(!inside){
                Paint warn=new Paint(Paint.ANTI_ALIAS_FLAG);
                warn.setColor(Color.rgb(200,100,0));
                warn.setTextSize(25);
                c.drawText(
                        "GPS outside WA5 boundary",
                        70,555,warn);
            }

            File f=new File(
                    folder,
                    baseName+"_location.png");

            FileOutputStream out=
                    new FileOutputStream(f);

            bmp.compress(
                    Bitmap.CompressFormat.PNG,
                    100,
                    out);

            out.close();
            bmp.recycle();

            return f;

        } catch(Exception e){
            return null;
        }
    }
}
