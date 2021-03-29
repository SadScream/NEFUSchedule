package com.sadscream.javanefuschedule;

import androidx.appcompat.app.AppCompatActivity;
import androidx.preference.PreferenceManager;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.webkit.WebView;
import android.widget.Button;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;

public class MainActivity extends AppCompatActivity {
    /*
    TODO: добавить кэш расписания
          может быть поменять текстовое поле для группы на селекторы
     */

    final String SCHEDULE_TAG = "PARAM";
    final String STATES_TAG = "States";
    final String DEBUG_TAG = "DebugInf";

    Color color = new Color(211, 211, 211);

    String currentGroup;
    Date currentDate;
    Parser parser;
    WebView raspView;
    Button updateButton, settingsButton;
    Intent fontIntent;
    PreferenceManager preferences;
    boolean boldSubjNames, showNextOnHolyday, showLecturerName;
    String fontSize;
    private String[] daysArray = new String[] {"Воскресенье", "Понедельник", "Вторник", "Среда", "Четверг", "Пятница", "Суббота"};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        constructor();
    }

    private void constructor() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

        currentGroup = preferences.getString("group_name", "");

        if (currentGroup.equals("")) {
            Log.d(DEBUG_TAG, "group equals ''");
            preferences.edit().putString("group_name", "ИМИ-БА-ИВТ-19-1").commit();
            currentGroup = "ИМИ-БА-ИВТ-19-1";
        }

        boldSubjNames = preferences.getBoolean("bold_font", true);
        fontSize = preferences.getString("font_size_list", "11pt");

        currentDate = new Date();
        fontIntent = new Intent(this, SettingsActivity.class);

        // initialize button mouse clicked listeners
        ButtonListener clickListener = new ButtonListener();

        parser = new Parser(currentGroup, currentDate, color);

        // creating instances of view objects
        raspView = findViewById(R.id.view_schedule);
        updateButton = (Button)findViewById(R.id.reload_btn);
        settingsButton = (Button)findViewById(R.id.settings_btn);

        updateButton.setOnClickListener(clickListener);
        settingsButton.setOnClickListener(clickListener);
    }

    private void updateSchedule() {
        Log.d(SCHEDULE_TAG, "groupName: "+currentGroup);
        Log.d(SCHEDULE_TAG, "date: "+currentDate.toString());
        Log.d(SCHEDULE_TAG, "bold_font: "+boldSubjNames);
        Log.d(SCHEDULE_TAG, "font_size: "+fontSize);
        Log.d(SCHEDULE_TAG, "next_week_on_sunday: "+showNextOnHolyday);
        Log.d(SCHEDULE_TAG, "show_lecturer_name: "+showLecturerName);

        Calendar cal = Calendar.getInstance();
        int day = cal.get(Calendar.DAY_OF_WEEK); // 1 - sunday, 2 - monday

        if (!showNextOnHolyday) {
            currentDate = new Date();
        }
        else {
            if (day == 1) {
                cal.add(Calendar.DATE, 7);
                currentDate = cal.getTime();
            }
        }

        parser.setGroup(currentGroup);
        parser.setFontParameters(boldSubjNames, fontSize);
        parser.setDayParameters(currentDate, daysArray[day-1]);
        parser.setAddictive(showLecturerName);

        buttonsSetEnabled(false);
        new ParserTask().execute(parser);
    }

    public void buttonsSetEnabled(boolean enabled) {
        settingsButton.setEnabled(enabled);
        updateButton.setEnabled(enabled);
    }

    @Override
    protected void onResume() {
        super.onResume();

        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        currentGroup = preferences.getString("group_name", "ИМИ-БА-ИВТ-19-1");
        boldSubjNames = preferences.getBoolean("bold_font", true);
        fontSize = preferences.getString("font_size_list", "11pt");
        showNextOnHolyday = preferences.getBoolean("next_week_on_sunday", false);
        showLecturerName = preferences.getBoolean("show_lecturer_names", false);

        updateSchedule();
    }

    public void onReloadButtonClicked() {
        currentDate = new Date();
        updateSchedule();
    }

    public void onSettingsButtonClicked() {
        startActivity(fontIntent);
    }

    private class ButtonListener implements View.OnClickListener {
        @Override
        public void onClick(View v) {
            switch (v.getId()) {
                case R.id.reload_btn:
                    onReloadButtonClicked();
                    break;
                case R.id.settings_btn:
                    onSettingsButtonClicked();
                    break;
            }
        }
    }

    public class ParserTask extends AsyncTask<Parser, Void, Pair<Boolean, String>> {

        @Override
        protected Pair<Boolean, String> doInBackground(Parser... parser) {

            Pair<Boolean, String> result = parser[0].parseAndGetStatus();
            return result;
        }

        @Override
        protected void onPostExecute(Pair<Boolean, String> result) {
            buttonsSetEnabled(true);
            if (result.getFirst()) {
                String html = parser.toHtml();
                raspView.loadData(html, "text/html; charset=UTF-8", null);
                Log.d(DEBUG_TAG, html);
            }
            else {
                String html_ = "<html>\n" +
                        "<head>\n" +
                        "<style type='text/css'>\n" +
                        "body {\n" +
                        "background-color: " + color.toString() + ";\n" +
                        "}\n" +
                        "</style>\n" +
                        "</head>\n" +
                        "<body>";
                raspView.loadData(html_+result.getSecond()+"</body></html>", "text/html; charset=UTF-8", null);
                Log.d(DEBUG_TAG, result.getSecond());
            }
        }
    }
}