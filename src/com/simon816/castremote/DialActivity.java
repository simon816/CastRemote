package com.simon816.castremote;

import android.app.Activity;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import com.simon816.castremote.youtube.YouTubeFragment;
import de.w3is.jdial.DialClient;
import de.w3is.jdial.DialClientConnection;
import de.w3is.jdial.model.Application;
import de.w3is.jdial.model.DialClientException;
import de.w3is.jdial.model.DialServer;

import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

public class DialActivity extends Activity {

    DialServer device;
    DialClientConnection connection;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dial);
        Intent intent = getIntent();
        this.device = (DialServer) intent.getSerializableExtra("device");
        System.out.println("Open dial device: " + device);
        findViewById(R.id.dial_start_yt).setOnClickListener(new View.OnClickListener() {

            @Override
            public void onClick(View v) {
                new AsyncTask<Object, Object, Object>() {

                    @Override
                    protected Object doInBackground(Object... params) {
                        try {
                            connection.startApplication(Application.YOUTUBE);
                        } catch (DialClientException e) {
                            // TODO Auto-generated catch block
                            e.printStackTrace();
                        }
                        return null;
                    }
                }.execute();
            }
        });
        new AsyncTask<Object, Object, Application>() {

            @Override
            protected Application doInBackground(Object... params) {
                connection = new DialClient().connectTo(device);
                return connection.getApplication(Application.YOUTUBE);
            }

            protected void onPostExecute(Application result) {
                if (result != null) {
                    System.out.println(result);
                    try {
                        // TODO: connect screenId to YouTubeFragment
                        String screenId = XPathFactory.newInstance().newXPath().evaluate("/screenId/text()", result.getAdditionalData());
                        getFragmentManager().beginTransaction().add(R.id.controller_container, new YouTubeFragment(), "YouTube").commit();
                    } catch (XPathExpressionException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                }
            }

        }.execute();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.dial, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
