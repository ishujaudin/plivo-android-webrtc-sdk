package com.plivo.endpoint;

import java.io.BufferedInputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class HttpPutTask {
    private String data;
    private HTTPRequestCallback callback;
    private String method;

    public HttpPutTask(String data, String method, HTTPRequestCallback callback) {
        if (data != null) {
            this.data = data;
            this.callback = callback;
            this.method = method;
        }
    }

    void execute(String url) {
        ExecutorService executor = Executors.newSingleThreadExecutor();
        executor.execute(() -> doInBackground(url));
    }


    protected void doInBackground(String urlString) {

        try {
            // This is getting the url from the string we passed in
            URL url = new URL(urlString);

            // Create the urlConnection
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
            urlConnection.setDoInput(true);
            urlConnection.setDoOutput(true);
            urlConnection.setRequestProperty("Content-Type", "text/plain");
            urlConnection.setRequestProperty( "charset", "utf-8");
            urlConnection.setRequestMethod(this.method);

            // Send the post body
            if (this.data != null) {
                OutputStreamWriter writer = new OutputStreamWriter(urlConnection.getOutputStream());
                writer.write(data);
                writer.flush();
            }

            int statusCode = urlConnection.getResponseCode();
            if (statusCode ==  200) {
                InputStream inputStream = new BufferedInputStream(urlConnection.getInputStream());
                String response = convertInputStreamToString(inputStream);
                callback.onResponse(response);
            } else {
                callback.onFailure(statusCode);
            }
        } catch (Exception e) {
            Log.D(e.getLocalizedMessage());
        }
    }

    private String convertInputStreamToString(InputStream inputStream) {
        BufferedReader bufferedReader = new BufferedReader( new InputStreamReader(inputStream));
        StringBuilder sb = new StringBuilder();
        String line;
        try {
            while((line = bufferedReader.readLine()) != null) {
                sb.append(line);
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return sb.toString();
    }
}
