package com.sadscream.javanefuschedule;

import android.util.Log;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jsoup.Connection;
import org.jsoup.Connection.Method;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;

public class Parser {
    private Color color;
    private String group, date, dayName = "";
    private String fontSize = "11pt";
    private boolean is_bold = true, show_lecturer_name = false;
    private Document doc = null;
    private Connection.Response response = null;
    private Pattern headPattern, dayOfWeekStringPattern, dayOfWeekPattern, timePattern, subgroupPattern; // регулярные выражения

    private String[] daysArray = new String[] {"ПОНЕДЕЛЬНИК", "ВТОРНИК", "СРЕДА", "ЧЕТВЕРГ", "ПЯТНИЦА", "СУББОТА"};
    private String[] timeArray = new String[] {"08:00-09:35", "09:50-11:25", "11:40-13:15", "14:00-15:35", "15:50-17:25", "17:30-19:15"};

    private String head; // заголовок вида ''Расписание занятий группы: <группа>(<не>четная неделя) на <дата>
    private HashMap<String, HashMap<String, ArrayList<String>>> dayTable; // тут хранится информация о каждом отдельном дне недели
    private HashMap<String, ArrayList<String>> subjectTable; // тут хранится информация о предметах в определенный день

    public static void main(String[] args) {
        String group = "ИМИ-БА-ИВТ-19-1";
        Date date = new Date();
        Color c = new Color(211, 211, 211);
        Parser parser = new Parser(group, date, c);
        Pair<Boolean, String> result = parser.parseAndGetStatus();

        if (result.getFirst()) {
            System.out.println("success");
        }
        else {
            System.out.println(result.getSecond());
        }

        System.out.println(parser.toStr());
    }

    public String toStr() {
        String result = "", day;

        if (doc == null) {
            return "";
        }

        result += head + "\n\n";

        for (int i = 0; i < 6; i++) {
            result = result + "\n" + daysArray[i] + "\n";

            for (int j = 0; j < 6; j++) {
                result = result + "\t" + timeArray[j] + " ";
                ArrayList<String> subjes = dayTable.get(daysArray[i]).get(timeArray[j]);

                for (int k = 0; k < subjes.size(); k++) {
                    if (k > 0) {
                        result += " \\ ";
                    }
                    result += subjes.get(k);
                }
                result += "\n";
            }
        }

        return result;
    }

    public String toHtml() {
        String result = "<html>\n" +
                "<head>\n" +
                "<style type='text/css'>\n" +
                "body {\n" +
                "background-color: " + color.toString() + ";\n" +
                "}\n" +
                "div {\n" +
                "font-size: " + fontSize + ";\n" +
                "position: absolute;\n" +
                "width: 100%;\n" +
                "height: 100%;\n" +
                "top: 0pt;\n" +
                "left: 0pt;\n" +
                "}\n" +
                "</style>\n" +
                "</head>\n" +
                "<body>\n" +
                "<div>";
        String day, line="<hr color='black' size='1pt'>";

        if (doc == null) {
            return "";
        }

        result += head + "<br>День: " + dayName + "<br>";

        for (int i = 0; i < 6; i++) {
            if (i == 5 && !dayTable.containsKey(daysArray[i])) {
                // когда в субботу не учатся
                break;
            }

            result = result + "<br><p align='center'><b>" + daysArray[i] + "</b></p>"+line;

            for (int j = 0; j < 6; j++) {
                result = result + "&nbsp;" + timeArray[j] + " ";

                ArrayList<String> subjes = dayTable.get(daysArray[i]).get(timeArray[j]);

                for (int k = 0; k < subjes.size(); k++) {
                    if (k > 0) {
                        result += " \\ ";
                    }
                    result += subjes.get(k);
                }
                result += line;
            }
        }

        result += "</div></body></html>";

        return result;
    }

    public Parser(String group, Date date, Color c) {
        this.group = group;
        this.date = new SimpleDateFormat("dd-MM-yyyy").format(date);
        this.color = c;
        System.out.println(this.date);

        headPattern = Pattern.compile("Расписание занятий группы: .+<b>");
        dayOfWeekStringPattern = Pattern.compile("th.+>[А-Я]+");
        dayOfWeekPattern = Pattern.compile("[А-Я]+");
        timePattern = Pattern.compile("\\d\\d:\\d\\d-\\d\\d:\\d\\d");
        subgroupPattern = Pattern.compile("<br>.*");
    }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setFontParameters(boolean boldSubjNames, String fontSize) {
        is_bold = boldSubjNames;
        this.fontSize = fontSize;
    }

    public void setDayParameters(Date date, String s) {
        this.date = new SimpleDateFormat("dd-MM-yyyy").format(date);
        dayName = s;
    }

    public void setAddictive(boolean showLecturerName) {
        show_lecturer_name = showLecturerName;
    }

    public Pair<Boolean, String> parseAndGetStatus() {
        Matcher matcher, matcherForDay;

        boolean state = makeDocument();

        if (!state) {
            return new Pair<>(false, "Произошла ошибка при получении документа. " +
                                                    "Проверьте интернет соединение и корректность имени группы в настройках.");
        }

        String pageHtml = doc.html();
        matcher = headPattern.matcher(pageHtml); // поиск заголовка

        if (matcher.find()) {
            head = pageHtml.substring(matcher.start(), matcher.end()-3); // -3 т.к. в конце мы захватываем '<b>'
        }
        else {
            return new Pair<>(false, "Не удалось получить корректные данные");
        }

        Elements elements = getTableElements(); // получение строк таблицы

        if (elements == null || elements.size() <= 1) {
            return new Pair<>(false, "Не удалось получить элементы таблицы");
        }

        dayTable = new HashMap<String, HashMap<String, ArrayList<String>>>();
        /*
         * представляет собой структуру, где ключом является день недели
         * а значением другая структура - subjectTable, в которой в свою очередь
         * ключом является время, а значением - массив строк - занятий которые проходят в это время
         */
        String currentDay = "", currentTime = "";

        for (Element element: elements) {
            if (element.className().equalsIgnoreCase("error")) {
                /*
                 * класс с именем 'error' имеют элементы, хранящие в себе день недели, например:
                 * <tr class="error">
                 *     <th colspan="5">ПОНЕДЕЛЬНИК 1 марта 2021</th>
                 * </tr>
                 * до тех пор, пока мы не встретим другой элемента класса 'error',
                 * все последующие элементы будут иметь класс 'success' и являться предметами,
                 * которые относятся к нашему дню
                 */

                subjectTable = new HashMap<String, ArrayList<String>>();
                subjectTable.put("08:00-09:35", new ArrayList<String>());
                subjectTable.put("09:50-11:25", new ArrayList<String>());
                subjectTable.put("11:40-13:15", new ArrayList<String>());
                subjectTable.put("14:00-15:35", new ArrayList<String>());
                subjectTable.put("15:50-17:25", new ArrayList<String>());
                subjectTable.put("17:30-19:15", new ArrayList<String>());

                // находим строку '<th colspan="5">ПОНЕДЕЛЬНИК 1 марта 2021</th>'
                matcher = dayOfWeekStringPattern.matcher(element.html());

                if (matcher.find()) {
                    // находим строку 'ПОНЕДЕЛЬНИК'
                    matcherForDay = dayOfWeekPattern.matcher(element.html());

                    if (matcherForDay.find()) {
                        // запоминаем, что последующие элементы с предметами будут относиться к этому дню
                        currentDay = element.html().substring(matcherForDay.start(), matcherForDay.end());
                        dayTable.put(currentDay, subjectTable);
                    }
                    else {
                        return new Pair<>(false, "Не удалось обнаружить день недели");
                    }
                }
                else {
                    return new Pair<>(false, "Не удалось обнаружить строку дня недели");
                }
            }
            else if (element.className().equalsIgnoreCase("success")) {
            	/*
            	 * элементы класса success являются предметами и имеют примерно следующую конструкцию:
            	 * <tr class="success">
     					<td>14:00-15:35</td>
     					<td><a>Физика</a> (Лабораторная работа) </td>
     					<td><a>Христофоров П.П.</a></td>
     					<td><a>310 КФЕН</a></td>
     					<td> С 27.01 по 26.05 <br>Подгруппа 2(0)</td>
    			   </tr>
    			   <tr class="success">
    			   		...
    			   		...
    			   </tr>
            	 */

                subjectTable = dayTable.get(currentDay);
                Elements subjectElementsArray = element.getElementsByTag("td");
                Element timeElement = subjectElementsArray.get(0); // получаем элемент <td>14:00-15:35</td>
                subjectElementsArray.remove(0);

                matcher = timePattern.matcher(timeElement.html()); // ищем строку '14:00-15:35'

                if (matcher.find()) {
                    currentTime = timeElement.html().substring(matcher.start(), matcher.end());
                } else {
                    return new Pair<>(false, "Не удалось обнаружить время");
                }

                String fullSubjectName = "", subjName, subjLector, subjCabinet, subgroupHTML, subgroup = "";

                if (is_bold)
                    subjName = "<b>"+subjectElementsArray.get(0).text()+"</b>";
                else
                    subjName = subjectElementsArray.get(0).text();

                if (show_lecturer_name)
                    subjLector = " " + subjectElementsArray.get(1).text(); // имя препода
                else
                    subjLector = "";

                subjCabinet = subjectElementsArray.get(2).text();

                // строка вида <td> С 27.01 по 26.05 <br>Подгруппа 2(0)</td>
                // при этом строка 'Подгруппа 2(0)' может отсутствовать
                subgroupHTML = subjectElementsArray.get(3).html();

                matcher = subgroupPattern.matcher(subgroupHTML); // поиск строки '<br>Подгруппа 2(0)</td>'

                if (matcher.find()) {
                    subgroup = subgroupHTML.substring(matcher.start()+4, matcher.end());
                    fullSubjectName = subjName + " " + subjCabinet + " " + subgroup + subjLector;
                }
                else {
                    fullSubjectName = subjName + " " + subjCabinet + subjLector;
                }

//                System.out.println(subjName + ";" + subjCabinet + ";" + subgroup + ";" + subjLector);
                subjectTable.get(currentTime).add(fullSubjectName);
            }
        }

        return new Pair<>(true, "");
    }

    private Elements getTableElements() {
        Elements tbodyArray = doc.getElementsByTag("tbody");

        if (tbodyArray.size() == 0) {
            return null;
        }

        Element tbody = tbodyArray.get(0);
        Elements elements = tbody.getElementsByTag("tr");
        return elements;
    }

    private boolean makeDocument() {
        boolean result = requestToPage(); // пытаемся выполнить запрос

        if (!result) return false; // false вернется в случае проблем с соединением

        int statusCode = getStatusCode(); // получаем status code(200 or 404 or 503 etc)

        if (statusCode != 200) return false;

        doc = getPage(); // пытаемся получить document объект

        if (doc == null) return false;

        return true;
    }

    private Document getPage() {
        Document _doc = null;
        try {
            _doc = response.parse();
            System.out.println("parsed");
        } catch (IOException e) {
            e.printStackTrace();
        }
        return _doc;
    }

    private boolean requestToPage() {
        HashMap<String, String> headers = new HashMap<String, String>(); // Request Headers
        headers.put("Host", "www.s-vfu.ru");
        headers.put("Connection", "keep-alive");
        headers.put("Accept", "*/*");
        headers.put("Origin", "https://www.s-vfu.ru");
        headers.put("X-Requested-With", "XMLHttpRequest");
        headers.put("Save-Data", "on");
        headers.put("User-Agent", "Mozilla/5.0 (Windows NT 6.1; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/78.0.3904.97 YaBrowser/19.12.0.358 Yowser/2.5 Safari/537.36");
        headers.put("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8");
        headers.put("Sec-Fetch-Site", "same-origin");
        headers.put("Sec-Fetch-Mode", "cors");
        headers.put("Referer", "https://www.s-vfu.ru/raspisanie/");
        headers.put("Accept-Encoding", "gzip, deflate, br");
        headers.put("Accept-Language", "ru,en;q=0.9");

        HashMap<String, String> data = new HashMap<String, String>(); // Form Data
        data.put("action", "showrasp");
        data.put("groupname", group);
        data.put("mydate", date);

        try {
            response = Jsoup.connect("https://www.s-vfu.ru/raspisanie/ajax.php")
                    .headers(headers)
                    .data(data)
                    .method(Method.POST)
                    .execute();
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    private int getStatusCode() {
        int statusCode = response.statusCode();
        return statusCode;
    }
}
