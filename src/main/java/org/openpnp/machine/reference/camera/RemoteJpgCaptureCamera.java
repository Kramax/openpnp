/*
 * Copyright (C) 2020 Greg Hjelstrom greg.hjelstrom@gmail.com
 * Copyright (C) 2024 Derek Ney derekney@gmail.com
 * 
 * This file is part of OpenPnP.
 * 
 * OpenPnP is free software: you can redistribute it and/or modify it under the terms of the GNU
 * General Public License as published by the Free Software Foundation, either version 3 of the
 * License, or (at your option) any later version.
 * 
 * OpenPnP is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY; without even
 * the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
 * Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License along with OpenPnP. If not, see
 * <http://www.gnu.org/licenses/>.
 * 
 * For more information about OpenPnP visit http://openpnp.org
 */


package org.openpnp.machine.reference.camera;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import javax.imageio.ImageIO;

import org.openpnp.gui.support.Wizard;
import org.openpnp.machine.reference.camera.wizards.RemoteJpgCaptureCameraWizard;
import org.openpnp.model.LengthUnit;
import org.openpnp.model.Location;
import org.openpnp.spi.PropertySheetHolder;
import org.simpleframework.xml.Attribute;

import jnr.ffi.Struct.in_addr_t;

import org.pmw.tinylog.Logger;


public class RemoteJpgCaptureCamera extends ReferenceCamera {

    @Attribute(required = false)
    private String remoteURL = "";
    // TEST URLs 
    // http://172.27.47.85:12345/capture-image

    @Attribute(required = false)
    private int width = 1280;

    @Attribute(required = false)
    private int height = 720;

    @Attribute(required = false)
    private int timeout = 3000;

    private HttpClient client = null;

    private HttpRequest request = null;

    private boolean dirty = false;

    BufferedImage blackImage = null;

    @Attribute(required = false)
    private int openRetryIntervalMillis = 10000; // 10 seconds
    private long retryTimeMillis = 0;
    @Attribute(required = false)
    private int requestTimeoutMillis = 240; // milliseconds before a request should time out


    public RemoteJpgCaptureCamera() {
        setUnitsPerPixel(new Location(LengthUnit.Millimeters, 0.04233, 0.04233, 0, 0));
    }

    public boolean isDirty() {
        return dirty;
    }

    public void setDirty(boolean dirty) {
        this.dirty = dirty;
    }

    public String getRemoteURL() {
        return remoteURL;
    }

    public void setRemoteURL(String url) {
        this.remoteURL = url;
        setDirty(true);
    }

    public String getURL() {
        return remoteURL;
    }

    public void setURL(String url) {
        String oldValue = this.remoteURL;
        this.remoteURL = url;
        firePropertyChange("remoteURL", oldValue, url);
        setDirty(true);
    }

    public int getTimeout() {
        return timeout;
    }

    public void setTimeout(int timeout) {
        this.timeout = timeout;
    }

    public int getRequestTimeout() {
        return requestTimeoutMillis;
    }

    public void setRequestTimeout(int timeout) {
        this.requestTimeoutMillis = timeout;
    }

    public int getOpenRetryInterval() {
        return openRetryIntervalMillis;
    }

    public void setOpenRetryInterval(int timeout) {
        this.openRetryIntervalMillis = timeout;
    }

    public int getCaptureWidth() {
        return width;
    }

    public void setCaptureWidth(int x) {
        this.width = x;
    }

    public int getCaptureHeight() {
        return height;
    }

    public void setCaptureHeight(int x) {
        this.height = x;
    }

    @Override 
    protected synchronized boolean ensureOpen() {
        if (remoteURL.isEmpty()) {
            return false;
        }
        return super.ensureOpen();
    }

    protected boolean maybeOpen() {
        try {
            client = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofMillis(getTimeout()))
                .build();

            request = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(remoteURL))
                .setHeader("User-Agent", "openpnp")
                .timeout(Duration.ofMillis(requestTimeoutMillis))
                .build();

            Logger.trace("MJPG stream " + remoteURL + " is connected.");
        }
        catch (Exception e) {
            Logger.trace("Exception opening MJPG stream " + remoteURL + ". Will retry in future");
            Logger.trace("Exception was: " + e);
            retryTimeMillis = System.currentTimeMillis() + openRetryIntervalMillis;
            return false;
        }

        return true;
    }

    @Override
    public void open() throws Exception {
        stop();
        client = null;
        request = null;
        if (!maybeOpen())
        {
            Logger.debug("Could not open MJPG stream " + remoteURL + " on first try, but will keep trying");
        }

        blackImage = new BufferedImage(1080, 720, BufferedImage.TYPE_INT_RGB);

        super.open();
    }

    @Override
    public void close() throws IOException {
        super.close();
        
        client = null;
        request = null;
    }

    protected void tempCloseAfterError() {
        client = null;
        request = null;
        retryTimeMillis = System.currentTimeMillis() + openRetryIntervalMillis;
    }

    protected BufferedImage handleIOException(IOException e, String message) {
        System.err.println(message + remoteURL + ": " + e.toString());
        e.printStackTrace();
        tempCloseAfterError();
        return null;
    }

    // if the socket is closed on the sending end we will start getting EOF on read (-1)
    // but we will not get an exception
    // we only get an exception from timeouts
    protected void handleEOF() {
        tempCloseAfterError();
        System.err.println("Looks like stream has closed, will try to reopen: " + remoteURL);
    }

    protected BufferedImage readImage(byte[] data) {
        int image_size = data.length;
        ByteArrayInputStream jpg_stream = new ByteArrayInputStream(data, 0, image_size);

        BufferedImage frame = null;

        try {
            frame = ImageIO.read(jpg_stream);
            // Logger.trace("Returning valid frame for " + mjpgURL);
            return frame;
        }
        catch (IOException e) {
            // e.printStackTrace();
            // assume we have junk SOF tags at end of data stream
            // we can fix it up by just removing it
            int junk_count = 32;
            int min_index = image_size - junk_count;

            if (min_index > 1) {
                for(int index = image_size - 2; index >= min_index; index--) {
                    // Logger.trace("trying to fixup with index=" + index + " with min_index=" + min_index + "and length=" + image_size + " and value=" + jpg_buffer[index]);
                    if (data[index] == -1) { // it is signed so 0xff is -1
                        // if index is where the 255 is, then that is the length we want to use
                        // as the last element of an array of length is at length-1 (index-1 in our case)
                        jpg_stream = new ByteArrayInputStream(data, 0, index);
                        try {
                            frame = ImageIO.read(jpg_stream);
                            // Logger.trace("Fixed up MJPEG frame of length " + image_size + " with length " + index + " for stream " + mjpgURL);
                            return frame;
                        }
                        catch (IOException e2) {
                            // Logger.trace("Attempt to fixup failed for MJPEG frame of length " + image_size + " with length " + index + " for stream " + mjpgURL);
                        }
                        // keep looking for more 0xff values
                    }
                }
                Logger.trace("Could not fix up MJPEG frame of length " + image_size + " for stream " + remoteURL);
                return null;
            }
            else {
                return null;
            }
        }
    }


    @Override
    public synchronized BufferedImage internalCapture() {
        if (!ensureOpen()) {
            return null;
        }

        if (request == null) {
            if (retryTimeMillis > 0) {
                if (System.currentTimeMillis() > retryTimeMillis) {
                    if (maybeOpen()) {
                        Logger.debug(
                            "Deferred open of MJPEG stream " + remoteURL + " succeeded."
                        );
                    }
                    else {
                        return blackImage;
                    }
                }
                else {
                    return blackImage;
                }
            }
            else {
                return blackImage;
            }
        }

        HttpResponse<byte[]> response = null;

        try {
            // CompletableFuture: A Future that may be explicitly completed (setting its value and status), and may be used as a CompletionStage, supporting dependent functions and actions that trigger upon its completion.
            // send: Sends the given request using this client, blocking if necessary to get the response.
            // The returned HttpResponse<T> contains the response status, headers, and body ( as handled by given response body handler ).
            response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());

            // HttpHeaders headers = response.headers();
            // Logger.trace("RJPGCapture: status code=" + response.statusCode());
            // Logger.trace("RJPGCapture: response length=" + response.body().length);
            // headers.map().forEach((k, v) -> Logger.trace("RJPGCapture: headers " + k + ":" + v));

            if (response.statusCode() != 200) {
                return blackImage;
            }
            return readImage(response.body());
        } catch (IOException e) {
            handleIOException(e, "exception sending HTTP request for frame");
            return blackImage;
        } catch (InterruptedException e) {
            Logger.trace("interrupted exception sending capture request");
            e.printStackTrace();
            return blackImage;
        }
    }

    @Override
    public Wizard getConfigurationWizard() {
        return new RemoteJpgCaptureCameraWizard(this);
    }

    @Override
    public String getPropertySheetHolderTitle() {
        return getClass().getSimpleName() + " " + getName();
    }

    @Override
    public PropertySheetHolder[] getChildPropertySheetHolders() {
        return null;
    }
}
