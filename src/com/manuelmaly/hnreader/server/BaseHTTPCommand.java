package com.manuelmaly.hnreader.server;

import java.io.Serializable;

import org.apache.http.client.HttpClient;
import org.apache.http.client.ResponseHandler;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpRequestBase;
import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.BasicHttpParams;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;

import android.content.Context;
import android.content.Intent;
import android.os.Parcelable;
import android.support.v4.content.LocalBroadcastManager;

/**
 * Generic base for HTTP calls via {@link HttpClient}, ideally to be started in a background thread.
 * When the call has finished, listeners are notified via an intent sent to {@link LocalBroadcastManager}, 
 * i.e. they must first register (intent name is configurable via notificationBroadcastIntentID). 
 * Response and errors are also sent via the intent. 
 * 
 * @author manuelmaly
 *
 * @param <T> class of response
 */
public abstract class BaseHTTPCommand<T extends Serializable> implements IAPICommand<T> {

    private String mNotificationBroadcastIntentID;
    private String mUrl;
    private String mURLQueryParams;
    private RequestType mType;
    private int mActualStatusCode;
    private Context mApplicationContext;
    private int mErrorCode = ERROR_UNKNOWN;
    private T mResponse;
    private Object mTag;
    private int mSocketTimeoutMS;
    private int mHttpTimeoutMS;

    public BaseHTTPCommand(final String url, final String queryParams, RequestType type,
        String notificationBroadcastIntentID, Context applicationContext, int socketTimeoutMS, int httpTimeoutMS) {
        mUrl = url;
        mURLQueryParams = queryParams;
        mType = type;
        mNotificationBroadcastIntentID = notificationBroadcastIntentID == null ? DEFAULT_BROADCAST_INTENT_ID
            : notificationBroadcastIntentID;
        mApplicationContext = applicationContext;
        mSocketTimeoutMS = socketTimeoutMS;
        mHttpTimeoutMS = httpTimeoutMS;
    }

    public void setTag(Object tag) {
        mTag = tag;
    }

    public Object getTag() {
        return mTag;
    }

    @Override
    public void run() {
        try {
            // Check if Device is currently offline:
            if (cancelBecauseDeviceOffline()) {
                notifyFinished();
                return;
            }

            // Start request, handle response in separate handler:
            HttpClient httpclient = new DefaultHttpClient(getHttpParams());
            httpclient.execute(setRequestData(createRequest()), getResponseHandler());
        } catch (Exception e) {
            setErrorCode(ERROR_GENERIC_COMMUNICATION_ERROR);
            notifyFinished();
        }
    }

    /**
     * Notify all registered observers
     */
    protected void notifyFinished() {
        Intent broadcastIntent = new Intent(mNotificationBroadcastIntentID);
        broadcastIntent.putExtra(BROADCAST_INTENT_EXTRA_ERROR, mErrorCode);
        broadcastIntent.putExtra(BROADCAST_INTENT_EXTRA_RESPONSE, mResponse);
        LocalBroadcastManager.getInstance(mApplicationContext).sendBroadcast(broadcastIntent);
    }

    /**
     * Returns TRUE if OFFLINE.
     * 
     * @return boolean true if offline, or false if online.
     */
    protected boolean cancelBecauseDeviceOffline() {
        if (mApplicationContext != null && !ConnectivityUtils.isDeviceOnline(mApplicationContext)) {
            setErrorCode(ERROR_DEVICE_OFFLINE);
            return true;
        }
        return false;
    }

    /**
     * Create a request object according to the request type set.
     * 
     * @return HttpRequestBase request object.
     */
    protected HttpRequestBase createRequest() {
        switch (mType) {
            case GET:
                return new HttpGet(getUrlWithParams());
            case PUT:
                return new HttpPut(getUrlWithParams());
            case DELETE:
                return new HttpDelete(getUrlWithParams());
            default:
                return new HttpPost(getUrlWithParams());
        }
    }

    protected String getUrlWithParams() {
        return mUrl + (mURLQueryParams != null ? "?" + mURLQueryParams : "");
    }

    public void responseHandlingFinished(T parsedResponse, int responseHttpStatus) {
        mActualStatusCode = responseHttpStatus;
        mResponse = parsedResponse;
        if (mActualStatusCode < 200 || mActualStatusCode >= 400)
            setErrorCode(ERROR_SERVER_RETURNED_ERROR);
        else if (mResponse == null)
            setErrorCode(ERROR_RESPONSE_PARSE_ERROR);
        else
            setErrorCode(ERROR_NONE);
        notifyFinished();
    }

    @Override
    public T getResponseContent() {
        return mResponse;
    }

    protected void setErrorCode(int errorCode) {
        mErrorCode = errorCode;
    }

    @Override
    public int getErrorCode() {
        return mErrorCode;
    }

    public int getActualStatusCode() {
        return mActualStatusCode;
    }

    private HttpParams getHttpParams() {
        HttpParams httpParameters = new BasicHttpParams();
        HttpConnectionParams.setConnectionTimeout(httpParameters, mHttpTimeoutMS);
        HttpConnectionParams.setSoTimeout(httpParameters, mSocketTimeoutMS);
        return httpParameters;
    }
    /**
     * Update the given request before it is sent over the wire.
     * 
     * @param request
     */
    abstract protected HttpUriRequest setRequestData(HttpUriRequest request);

    abstract protected ResponseHandler<T> getResponseHandler();

}
