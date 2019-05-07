package com.mapbox.mapboxsdk.plugins.offline.offline;

import com.mapbox.mapboxsdk.plugins.offline.model.GroupedOfflineDownloadOptions;

public interface GroupedOfflineDownloadChangeListener {

  void onSuccess(GroupedOfflineDownloadOptions groupedOfflineDownload);

  void onCancel(GroupedOfflineDownloadOptions groupedOfflineDownload);

  void onError(GroupedOfflineDownloadOptions groupedOfflineDownload, String error, String message);

  void onProgress(GroupedOfflineDownloadOptions groupedOfflineDownload);

}
