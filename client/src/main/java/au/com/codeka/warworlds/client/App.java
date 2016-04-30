package au.com.codeka.warworlds.client;

import android.app.Application;
import android.os.Handler;

import com.google.common.base.Preconditions;

import au.com.codeka.warworlds.client.concurrency.TaskRunner;
import au.com.codeka.warworlds.client.concurrency.Threads;
import au.com.codeka.warworlds.client.net.Server;
import au.com.codeka.warworlds.client.store.DataStore;
import au.com.codeka.warworlds.common.Log;

/**
 * Global {@link Application} object.
 */
public class App extends Application {
  public static App i;

  private static final Log log = new Log("App");
  private final TaskRunner taskRunner;
  private final Server server;
  private final DataStore dataStore;

  public App() {
    Preconditions.checkState(i == null);
    i = this;

    taskRunner = new TaskRunner();
    server = new Server();
    dataStore = new DataStore();
  }

  @Override
  public void onCreate() {
    super.onCreate();
    LogImpl.setup();

    Threads.UI.setThread(Thread.currentThread(), new Handler());

    server.connect();
    dataStore.open(this);

    log.info("App.onCreate() complete.");
  }

  public TaskRunner getTaskRunner() {
    return taskRunner;
  }
}