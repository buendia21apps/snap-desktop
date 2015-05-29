/*
 * Copyright (C) 2015 by Array Systems Computing Inc. http://www.array.ca
 *
 * This program is free software; you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation; either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, see http://www.gnu.org/licenses/
 */
package org.esa.snap.rcp.toolviews.productlibrary.model;

import org.esa.snap.rcp.SnapApp;
import org.esa.snap.util.Guardian;

import java.awt.*;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.prefs.Preferences;

/**
 * This class handles the configuration of the ProductGrabber.
 */
public class ProductLibraryConfig {

    private static final String WINDOW_LOCATION_X_KEY = "productLibrary.window.locationX";
    private static final String WINDOW_LOCATION_Y_KEY = "productLibrary.window.locationY";
    private static final String WINDOW_WIDTH_KEY = "productLibrary.window.width";
    private static final String WINDOW_HEIGHT_KEY = "productLibrary.window.height";
    private static final String BASE_DIR = "productLibrary.baseDir_";

    private final Preferences pref;

    /**
     * Creates a new instance with the given {@link org.esa.snap.util.PropertyMap}.
     * The property map which is used to load and store the configuration.
     *
     * @param preferences the {@link org.esa.snap.util.PropertyMap}.
     */
    public ProductLibraryConfig(final Preferences preferences) {
        Guardian.assertNotNull("preferences", preferences);
        pref = preferences;
    }

    /**
     * Sets the repositories.
     *
     * @param baseDir the repository base directory.
     */
    public void addBaseDir(final File baseDir) {
        pref.put(BASE_DIR + baseDir.getAbsolutePath(), baseDir.getAbsolutePath());
    }

    /**
     * removes the repositories.
     *
     * @param baseDir the repository base directory.
     */
    public void removeBaseDir(final File baseDir) {
        pref.put(BASE_DIR + baseDir.getAbsolutePath(), null);
    }

    /**
     * Retrieves the stored repositories.
     *
     * @return the stored repositories.
     */
    public File[] getBaseDirs() {
        final List<File> dirList = new ArrayList<>();
        try {
            for (String key : pref.keys()) {
                if (key.startsWith(BASE_DIR)) {
                    final String path = pref.get(key, null);
                    if (path != null) {
                        final File file = new File(path);
                        if (file.exists()) {
                            dirList.add(file);
                        }
                    }
                }
            }
        } catch (Exception e) {
            SnapApp.getDefault().handleError("Product Library unable to reload base folders", e);
        }
        return dirList.toArray(new File[dirList.size()]);
    }

    /**
     * Sets the window bounds of the ProductGrabber dialog.
     *
     * @param windowBounds the window bounds.
     */
    public void setWindowBounds(final Rectangle windowBounds) {
        pref.put(WINDOW_LOCATION_X_KEY, String.valueOf(windowBounds.x));
        pref.put(WINDOW_LOCATION_Y_KEY, String.valueOf(windowBounds.y));
        pref.put(WINDOW_WIDTH_KEY, String.valueOf(windowBounds.width));
        pref.put(WINDOW_HEIGHT_KEY, String.valueOf(windowBounds.height));
    }

    /**
     * Retrieves the window bounds of the ProductGrabber dialog.
     *
     * @return the window bounds.
     */
    public Rectangle getWindowBounds() {
        final int x = Integer.parseInt(pref.get(WINDOW_LOCATION_X_KEY, "50"));
        final int y = Integer.parseInt(pref.get(WINDOW_LOCATION_Y_KEY, "50"));
        final int width = Integer.parseInt(pref.get(WINDOW_WIDTH_KEY, "700"));
        final int height = Integer.parseInt(pref.get(WINDOW_HEIGHT_KEY, "450"));

        return new Rectangle(x, y, width, height);
    }
}