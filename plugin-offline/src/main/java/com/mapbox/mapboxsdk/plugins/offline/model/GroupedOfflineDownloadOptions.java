package com.mapbox.mapboxsdk.plugins.offline.model;

import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import com.google.auto.value.AutoValue;

import java.util.Collections;
import java.util.List;

@AutoValue
public abstract class GroupedOfflineDownloadOptions implements Parcelable {

  @NonNull
  public abstract List<OfflineDownloadOptions> offlineDownloadOptionsList();

  public abstract NotificationOptions notificationOptions();

  public abstract int progress();

  @Nullable
  public abstract OfflineDownloadOptions currentOfflineDownload();

  public static Builder builder() {
    return new AutoValue_GroupedOfflineDownloadOptions.Builder()
      .offlineDownloadOptionsList(Collections.<OfflineDownloadOptions>emptyList())
      .progress(0)
      .currentOfflineDownload(null);
  }

  public abstract Builder toBuilder();

  @AutoValue.Builder
  public abstract static class Builder {

    public abstract Builder offlineDownloadOptionsList(@NonNull List<OfflineDownloadOptions> offlineDownloadOptionsList);

    public abstract Builder notificationOptions(@NonNull NotificationOptions notificationOptions);

    public abstract Builder progress(int progress);

    public abstract Builder currentOfflineDownload(@Nullable OfflineDownloadOptions currentOfflineDownload);

    public abstract GroupedOfflineDownloadOptions build();
  }
}
