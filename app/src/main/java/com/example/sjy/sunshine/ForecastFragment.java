package com.example.sjy.sunshine;

/**
 * Created by SJY on 2016/4/16.
 */

import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.format.Time;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;

public class ForecastFragment extends Fragment {

   private ArrayAdapter<String> mForecastAdapter;
    ListView listView;
    String[] data = {
            "Mon 6/23 - Sunny - 31/17",
            "Tue 6/24 - Foggy - 21/8",
            "Wed 6/25 - Cloudy - 22/17",
            "Thurs 6/26 - Rainy - 18/11",
            "Fri 6/27 - Foggy - 21/10",
            "Sat 6/28 - TRAPPED  - 23/18",
            "Sun 6/29 - Sunny - 20/7"
    };
    ArrayList<String> weather = new ArrayList<>(Arrays.asList(data));

    //空白构造器
    public ForecastFragment() {

    }

    //加载menu，是下面的optionmenu生效
    @Override
    public void onCreate( Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    //创建菜单
    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_main,menu);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if(id == R.id.action_refresh) {
            //Toast.makeText(getActivity(), "refresh", Toast.LENGTH_SHORT).show();
            FetchWeatherTask weatherTask=new FetchWeatherTask();
            weatherTask.execute("CN101190101");
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View rootView = inflater.inflate(R.layout.fragment_main, container, false);
        mForecastAdapter = new ArrayAdapter<>(
                getActivity(),
                R.layout.list_item_forecast,
                R.id.list_item_forecast_textview,
                weather
        );
        listView = (ListView) rootView.findViewById(R.id.listview_forecast);
        listView.setAdapter(mForecastAdapter);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener(){
            @Override
            public void onItemClick(AdapterView<?>adapterView,View view,int position,long id){
               String forecast=mForecastAdapter.getItem(position);
                Toast.makeText(getActivity(),forecast,Toast.LENGTH_SHORT).show();
            }
        });
        return rootView;
    }


    public class FetchWeatherTask extends AsyncTask<String,Void,String[]>{
        /* The date/time conversion code is going to be moved outside the asynctask later,
        * so for convenience we're breaking it out into its own method now.
        */
        private String getReadableDateString(long time){
            // Because the API returns a unix timestamp (measured in seconds),
            // it must be converted to milliseconds in order to be converted to valid date.
            SimpleDateFormat shortenedDateFormat = new SimpleDateFormat("EEE MMM dd");
            return shortenedDateFormat.format(time);
        }

        /**
         * Prepare the weather high/lows for presentation.
         */
        private String formatHighLows(double high, double low) {
            // For presentation, assume the user doesn't care about tenths of a degree.
            long roundedHigh = Math.round(high);
            long roundedLow = Math.round(low);

            String highLowStr = roundedHigh+"℃" + "/" + roundedLow+"℃";
            return highLowStr;
        }

        /**
         * Take the String representing the complete forecast in JSON Format and
         * pull out the data we need to construct the Strings needed for the wireframes.
         *
         * Fortunately parsing is easy:  constructor takes the JSON string and converts it
         * into an Object hierarchy for us.
         */
        private String[] getWeatherDataFromJson(String forecastJsonStr, int numDays)
                throws JSONException {

            // These are the names of the JSON objects that need to be extracted.
           /* final String OWM_LIST = "list";
            final String OWM_WEATHER = "weather";
            final String OWM_MAX = "temp_max";
            final String OWM_MIN = "temp_min";
            final String OWM_DESCRIPTION = "main";
            final String OWM_CITY="name";
            final String OWM_POSITION="city";

            JSONObject forecastJson = new JSONObject(forecastJsonStr);
            JSONArray weatherArray = forecastJson.getJSONArray(OWM_LIST);
            JSONObject city=new JSONObject(forecastJsonStr);
            JSONObject city_m=city.getJSONObject(OWM_POSITION);
            String position=city_m.getString(OWM_CITY);*/

            // OWM returns daily forecasts based upon the local time of the city that is being
            // asked for, which means that we need to know the GMT offset to translate this data
            // properly.

            // Since this data is also sent in-order and the first day is always the
            // current day, we're going to take advantage of that to get a nice
            // normalized UTC date for all of our weather.
            final String OWM_DATA_SERVICE="HeWeather data service 3.0";
            final String OWM_CITY="city";
            final String OWM_BASIC="basic";
            final String OWM_DAILYFORECAST="daily_forecast";
            final String OWM_COND="cond";
            final String OWM_TXTD="txt_d";
            final String OWM_DATE="date";
            final String OWM_TEMP="tmp";
            final String OWM_MAX="max";
            final String OWM_MIN="min";


            JSONObject forecastJson=new JSONObject(forecastJsonStr);
            JSONArray weatherArray=forecastJson.getJSONArray(OWM_DATA_SERVICE).getJSONObject(0).getJSONArray(OWM_DAILYFORECAST);

            Time dayTime = new Time();
            dayTime.setToNow();

            // we start at the day returned by local time. Otherwise this is a mess.
            int julianStartDay = Time.getJulianDay(System.currentTimeMillis(), dayTime.gmtoff);

            // now we work exclusively in UTC
            dayTime = new Time();

            String[] resultStrs = new String[numDays];
            for(int i = 0; i < weatherArray.length(); i++) {
                // For now, using the format "Day, description, hi/low"
                String day;
                String description;
                String highAndLow;

                // Get the JSON object representing the day
                JSONObject dayForecast = weatherArray.getJSONObject(i);

                // The date/time is returned as a long.  We need to convert that
                // into something human-readable, since most people won't read "1400356800" as
                // "this saturday".
                long dateTime;
                // Cheating to convert this to UTC time, which is what we want anyhow
                dateTime = dayTime.setJulianDay(julianStartDay+i);
                day = getReadableDateString(dateTime);

                // description is in a child array called "weather", which is 1 element long.
                JSONObject weatherObject = dayForecast.getJSONObject(OWM_COND);
                description = weatherObject.getString(OWM_TXTD);

                // Temperatures are in a child object called "temp".  Try not to name variables
                // "temp" when working with temperature.  It confuses everybody.
                JSONObject temperatureObject = dayForecast.getJSONObject(OWM_TEMP);
                double high = temperatureObject.getDouble(OWM_MAX);
                double low = temperatureObject.getDouble(OWM_MIN);

                JSONObject position=forecastJson.getJSONArray(OWM_DATA_SERVICE).getJSONObject(0).getJSONObject(OWM_BASIC);
                String city=position.getString(OWM_CITY);

                highAndLow = formatHighLows(high, low);
                resultStrs[i] = day + " - " +" "+city+" "+ description + " - " + highAndLow;
            }

            for (String s : resultStrs) {
                Log.v(LOG_TAG, "Forecast entry: " + s);
            }
            return resultStrs;

        }
        private final String LOG_TAG = FetchWeatherTask.class.getSimpleName();
        @Override
        protected String[] doInBackground(String... params){
            // These two need to be declared outside the try/catch
            // so that they can be closed in the finally block.
            if(params.length==(0)){
                return null;
            }
            HttpURLConnection urlConnection = null;
            BufferedReader reader = null;

            // Will contain the raw JSON response as a string.
            String forecastJsonStr = null;

            //String format="json";
            //String units="mertic";
            int numDays=7;

            try {
                // Construct the URL for the OpenWeatherMap query
                // Possible parameters are avaiable at OWM's forecast API page, at
                // http://openweathermap.org/API#forecast
                final String FORECAST_BASE_URL="https://api.heweather.com/x3/weather?cityid=CN101190101&key=c5c8626e8fc541d29437c6eed5e81dac";
                //final String QUERY_PARAM="q";
                //final String FORMAT_PARAM="mode";
                //final String UNITS_PARAM="units";
                //final String DAYS_PARAM="cnt";

                final String CITY="cityid";
                final String KEY="key";
                final String API_KEY="c5c8626e8fc541d29437c6eed5e81dac";

                //Uri builtUri=Uri.parse(FORECAST_BASE_URL).buildUpon()
                  //      .appendQueryParameter(QUERY_PARAM,params[0])
                    //    .appendQueryParameter(FORMAT_PARAM,format)
                      //  .appendQueryParameter(UNITS_PARAM,units)
                        //.appendQueryParameter(DAYS_PARAM,Integer.toString(numDays))
                       // .build();

                Uri builtUri=Uri.parse(FORECAST_BASE_URL).buildUpon()
                        .appendQueryParameter(CITY,params[0])
                        .appendQueryParameter(KEY,API_KEY)
                        .build();


                URL url = new URL(builtUri.toString());

                Log.v(LOG_TAG,"Built URI"+builtUri.toString());

                // Create the request to OpenWeatherMap, and open the connection
                urlConnection = (HttpURLConnection) url.openConnection();
                urlConnection.setRequestMethod("GET");
                urlConnection.connect();

                // Read the input stream into a String
                InputStream inputStream = urlConnection.getInputStream();
                StringBuffer buffer = new StringBuffer();
                if (inputStream == null) {
                    // Nothing to do.
                    return null;
                }
                reader = new BufferedReader(new InputStreamReader(inputStream));

                String line;
                while ((line = reader.readLine()) != null) {
                    // Since it's JSON, adding a newline isn't necessary (it won't affect parsing)
                    // But it does make debugging a *lot* easier if you print out the completed
                    // buffer for debugging.
                    buffer.append(line + "\n");
                }

                if (buffer.length() == 0) {
                    // Stream was empty.  No point in parsing.
                    return null;
                }
                forecastJsonStr = buffer.toString();
            } catch (IOException e) {
                Log.e("PlaceholderFragment", "Error ", e);
                // If the code didn't successfully get the weather data, there's no point in attemping
                // to parse it.
                return null;
            } finally {
                if (urlConnection != null) {
                    urlConnection.disconnect();
                }
                if (reader != null) {
                    try {
                        reader.close();
                    } catch (final IOException e) {
                        Log.e("PlaceholderFragment", "Error closing stream", e);
                    }
                }
            }
            try {
                return getWeatherDataFromJson(forecastJsonStr,numDays);
            }catch (JSONException e){
                Log.e(LOG_TAG,e.getMessage(),e);
                e.printStackTrace();
            }
            return null;
        }
        @Override
        protected void onPostExecute(String[] result){
            if(result!=null){
                mForecastAdapter.clear();
                for(String dayForecastStr:result){
                    mForecastAdapter.add(dayForecastStr);
                }
            }
        }
    }
}