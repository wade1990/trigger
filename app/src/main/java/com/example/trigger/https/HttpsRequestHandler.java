package com.example.trigger.https;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import javax.net.ssl.SSLHandshakeException;
import javax.net.ssl.SSLContext;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLSession;

import android.os.AsyncTask;
import android.util.Log;

import com.example.trigger.MainActivity.Action;
import com.example.trigger.HttpsDoorSetup;
import com.example.trigger.DoorReply;
import com.example.trigger.DoorReply.ReplyCode;
import com.example.trigger.OnTaskCompleted;
import com.example.trigger.Utils;


public class HttpsRequestHandler extends AsyncTask<Object, Void, DoorReply> {
    private OnTaskCompleted listener;

    public HttpsRequestHandler(OnTaskCompleted listener) {
        this.listener = listener;
    }

    @Override
    protected DoorReply doInBackground(Object... params) {
        if (params.length != 2) {
            Log.e("HttpsRequestHandler", "Unexpected number of params.");
            return DoorReply.internal_error();
        }

        if (!(params[0] instanceof Action && params[1] instanceof HttpsDoorSetup)) {
            Log.e("HttpsRequestHandler", "Invalid type of params.");
            return DoorReply.internal_error();
        }

        Action action = (Action) params[0];
        HttpsDoorSetup setup = (HttpsDoorSetup) params[1];

        if (setup.getId() < 0) {
            return DoorReply.internal_error();
        }

        String command = "";

        switch (action) {
            case open_door:
                command = setup.open_query;
                break;
            case close_door:
                command = setup.close_query;
                break;
            case update_state:
                command = setup.status_query;
                break;
        }

        if (command.isEmpty()) {
            // ignore
            return new DoorReply(ReplyCode.LOCAL_ERROR, "");
        }

        try {
            URL url = new URL(command);

            // hostname verification
            if (setup.ignore_hostname_mismatch) {
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        // ignore hostname mismatch
                        return true;
                    }
                });
            } else {
                HttpsURLConnection.setDefaultHostnameVerifier(new HostnameVerifier() {
                    @Override
                    public boolean verify(String hostname, SSLSession session) {
                        return false;
                    }
                });
            }

            // certificate verification
            if (setup.certificate != null) {
                // custom certificate
                HttpsURLConnection.setDefaultSSLSocketFactory(
                    Utils.getFactoryWithCertificate(setup.certificate)
                );
            } else {
                // system certificate
                HttpsURLConnection.setDefaultSSLSocketFactory(
                    SSLContext.getDefault().getSocketFactory()
                );
            }

            HttpURLConnection con = (HttpURLConnection) url.openConnection();
            con.setConnectTimeout(2000);
            con.setRequestMethod ("GET");

            String result = readStream(con.getInputStream());

            if (con.getResponseCode() == 200) {
                return new DoorReply(ReplyCode.SUCCESS, result);
            } else {
                return new DoorReply(ReplyCode.REMOTE_ERROR, con.getResponseMessage());
            }
        } catch (MalformedURLException mue) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Malformed URL.");
        } catch (FileNotFoundException e) {
            return new DoorReply(ReplyCode.REMOTE_ERROR, "Server responds with an error.");
        } catch (java.net.SocketTimeoutException ste) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Server not reachable.");
        } catch (java.net.SocketException se) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, "Not connected to network.");
        //} catch (java.security.cert.CertPathValidatorException e) {
        //	return new DoorReply(ReplyCode.LOCAL_ERROR, "Certificate validation failed.");
        } catch (Exception e) {
            return new DoorReply(ReplyCode.LOCAL_ERROR, e.toString());
        }
    }

    private String readStream(InputStream in) throws SSLHandshakeException {
        BufferedReader reader = null;
        String result ="";

        try {
            reader = new BufferedReader(new InputStreamReader(in));
            String line = "";

            // read at maximum 50KB
            while (((line = reader.readLine()) != null) && (line.length() < 50000)) {
                result += line;
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            if (reader != null) {
                try {
                    reader.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }

        return result;
    }

    protected void onPostExecute(DoorReply result) {
        listener.onTaskCompleted(result);
    }
}
