package org.wildstang.wildrank.androidv2;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SyncUtilities {

    public static final int REQUEST_CODE_OPEN = 42;
    private static String databaseFolder = null;

    public static boolean isFlashDriveConnected(Context context) {
        return new File(getExternalRootDirectory(context)).exists();
    }

    public static boolean useSAF() {
        return android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP;
    }

    public static void copyFromStreamToStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[1024];

        int length;

        while ((length = in.read(buffer)) > 0) {
            out.write(buffer, 0, length);
        }

        in.close();
        out.close();
    }

    public static String getExternalRootDirectory(Context context) {
        if (databaseFolder == null) {
            //The location of the temp internal database used for sync should be
            //in the cache, since it is only needed for <1min at a time, and
            //created every time it is needed
            if (useSAF()) {
                File basePath = new File(context.getCacheDir() + File.separator + "externalDatabaseSync" + File.separator);
                databaseFolder = basePath.getPath();
            } else {
                return "/storage/usbdisk0/WildRank/cblite";
            }
        }
        return databaseFolder;
    }

    public static void openFileChooser(Activity activity) {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("*/*");
        activity.startActivityForResult(intent, SyncUtilities.REQUEST_CODE_OPEN);
    }

    public static boolean isOkAndOpen(int requestCode, int resultCode) {
        return requestCode == REQUEST_CODE_OPEN && resultCode == Activity.RESULT_OK;
    }

    public static void copyExternalToInternal(InputStream externalDatabaseStream, Context context) throws IOException {
        File internalDBCopyFolder = new File(SyncUtilities.getExternalRootDirectory(context) + File.separator + "wildrank.cblite2");
        File internalDBCopyFile = new File(internalDBCopyFolder.getPath() + File.separator + "db.sqlite3");
        if (!internalDBCopyFile.exists()) {
            internalDBCopyFolder.mkdirs();
            internalDBCopyFile.createNewFile();
        }
        OutputStream internalDBCopyStream = new FileOutputStream(internalDBCopyFile);
        copyFromStreamToStream(externalDatabaseStream, internalDBCopyStream);
    }
}
