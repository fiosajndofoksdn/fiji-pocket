package org.fijipocket;

import android.app.Activity;
import android.content.Context;
import android.content.res.Resources;
import org.fijipocket.android.R;

/** App chrome only; scientific image rendering does not use this palette. */
final class UiTheme {
    final int background,panel,line,text,muted,accent,button,selected,demo,workspace,grid,imageBorder,onAccent;
    private UiTheme(int background,int panel,int line,int text,int muted,int accent,int button,int selected,int demo,int workspace,int grid,int imageBorder,int onAccent){
        this.background=background;this.panel=panel;this.line=line;this.text=text;this.muted=muted;this.accent=accent;
        this.button=button;this.selected=selected;this.demo=demo;this.workspace=workspace;this.grid=grid;this.imageBorder=imageBorder;this.onAccent=onAccent;
    }
    private static final UiTheme DARK=new UiTheme(0xff10181e,0xff172229,0xff293942,0xffe8f0f2,0xff95a9b5,0xff56dac6,0xff22323b,0xff25413f,0xff203a37,0xff0b1217,0xff1b252c,0xff384852,0xff10181e);
    private static final UiTheme LIGHT=new UiTheme(0xfff3f6f7,0xffffffff,0xffcdd9de,0xff182a32,0xff516773,0xff08786c,0xffe5ecef,0xffd9eee9,0xffe0f2ed,0xffe7eef1,0xffcdd9df,0xffaabbc4,0xffffffff);
    static boolean isDark(Context context){return context.getSharedPreferences("appearance",Context.MODE_PRIVATE).getBoolean("dark",true);}
    static UiTheme colors(Context context){return isDark(context)?DARK:LIGHT;}
    static void save(Context context,boolean dark){context.getSharedPreferences("appearance",Context.MODE_PRIVATE).edit().putBoolean("dark",dark).apply();}
    static void apply(Activity activity){
        int style=isDark(activity)?R.style.AppTheme:R.style.AppTheme_Light;
        Resources.Theme fresh=activity.getResources().newTheme();fresh.applyStyle(style,true);
        activity.setTheme(style);activity.getTheme().setTo(fresh);
    }
}
