package com.simon816.castremote;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import su.litvak.chromecast.api.v2.ChromeCast;
import su.litvak.chromecast.api.v2.ChromeCasts;
import su.litvak.chromecast.api.v2.ChromeCastsListener;

import java.io.IOException;

public class MainActivity extends Activity {

    public static class Listener implements ChromeCastsListener {

        private Activity activity;
        BaseAdapter listAdapter;

        public Listener(Activity activity, BaseAdapter listAdapter) {
            this.activity = activity;
            this.listAdapter = listAdapter;
        }

        @Override
        public void newChromeCastDiscovered(ChromeCast chromeCast) {
            System.out.println("Found chromecast " + chromeCast);
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    listAdapter.notifyDataSetChanged();
                }
            });
        }

        @Override
        public void chromeCastRemoved(ChromeCast chromeCast) {
            activity.runOnUiThread(new Runnable() {

                @Override
                public void run() {
                    listAdapter.notifyDataSetChanged();
                }
            });
        }
    }

    CastDiscovery discovery = new CastDiscovery();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Button startScaning = (Button) findViewById(R.id.start_scanning);
        final Context context = this;
        BaseAdapter listAdapter = new BaseAdapter() {

            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                TextView view = new TextView(context);
                view.setText(getChromecast(position).getTitle());
                return view;
            }

            @Override
            public long getItemId(int position) {
                return getChromecast(position).getAddress().hashCode();
            }

            private ChromeCast getChromecast(int index) {
                return ChromeCasts.get().get(index);
            }

            @Override
            public Object getItem(int position) {
                return getChromecast(position);
            }

            @Override
            public int getCount() {
                return ChromeCasts.get().size();
            }
        };
        ChromeCasts.registerListener(new Listener(this, listAdapter));
        startScaning.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View view) {
                discovery.toggleState();
            }
        });
        ListView listView = (ListView) findViewById(R.id.chromecast_list);
        listView.setAdapter(listAdapter);
        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                discovery.setScanning(false);
                openChromecast(ChromeCasts.get().get(position));
            }
        });
        Button openTest = (Button) findViewById(R.id.open_test);
        openTest.setOnClickListener(new OnClickListener() {

            @Override
            public void onClick(View v) {
                discovery.setScanning(false);
                ChromeCast chromeCast = new ChromeCast("192.168.0.49", 8009);
                chromeCast.setName("Chromecast-d994f4051217fceefc514528504e4d34");
                openChromecast(chromeCast);
            }
        });
    }

    void openChromecast(ChromeCast chromeCast) {
        Intent intent = new Intent(this, CastActivity.class);
        intent.putExtra("name", chromeCast.getName());
        intent.putExtra("address", chromeCast.getAddress());
        intent.putExtra("port", chromeCast.getPort());
        intent.putExtra("appsURL", chromeCast.getAppsURL());
        intent.putExtra("application", chromeCast.getApplication());
        intent.putExtra("title", chromeCast.getTitle());
        intent.putExtra("appTitle", chromeCast.getAppTitle());
        intent.putExtra("model", chromeCast.getModel());
        this.startActivity(intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
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

    public class CastDiscovery {

        boolean isScanning = false;

        public void stop() {
            new AsyncTask<Object, Object, Object>() {

                @Override
                protected Object doInBackground(Object... params) {
                    try {
                        ChromeCasts.stopDiscovery();
                        isScanning = false;
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    return null;
                }

                protected void onPostExecute(Object result) {
                    Button startScaning = (Button) findViewById(R.id.start_scanning);
                    startScaning.setText("Start Scanning");
                };

            }.execute();
        }

        public void toggleState() {
            setScanning(!isScanning);
        }

        public void setScanning(boolean doScan) {
            if (doScan)
                start();
            else
                stop();
        }

        public boolean isScanning() {
            return isScanning;
        }

        public void start() {
            new AsyncTask<Object, Object, Object>() {

                @Override
                protected Object doInBackground(Object... params) {
                    try {
                        ChromeCasts.startDiscovery();
                        isScanning = true;
                    } catch (IOException e) {
                        // TODO Auto-generated catch block
                        e.printStackTrace();
                    }
                    return null;
                }

                protected void onPostExecute(Object result) {
                    Button startScaning = (Button) findViewById(R.id.start_scanning);
                    startScaning.setText("Stop Scanning");
                };
            }.execute();
        }

    }

}
