package com.littlefluffytoys.beebdroid;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpUriRequest;
import org.json.JSONArray;
import org.json.JSONException;

import common.ImageCache;
import common.Network;
import common.Utils;

import android.app.Activity;
import android.content.Intent;
import android.graphics.drawable.BitmapDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.FragmentTabHost;
import android.support.v4.app.ListFragment;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Toast;


import com.nononsenseapps.filepicker.FilePickerFragment;


public class LoadDisk extends AppCompatActivity implements FilePickerFragment.OnFilePickedListener {
	private static final String TAG="LoadDisk";
	
	private static final String SERVER_ROOT = "http://www.stairwaytohell.com/";
	//private static final String SERVER_ROOT = "http://www.littlefluffytoys.com/beebdroid/";
	private static final int ID_DELETE = 1;
	public static final int ID_RESULT_LOADDISK = 101;
	public static final int ID_RESULT_SAVE = 102;
	public static final int ID_RESULT_RESTORE = 103;
	
	protected FragmentTabHost mTabHost;

    public static class GamesListFragment extends ListFragment {
        public GamesListFragment() {
            setListAdapter(adapter);
            refreshOnlineList();
        }

        List<DiskInfo> onlineDisks = new ArrayList<DiskInfo>();
        BaseAdapter adapter = new BaseAdapter() {

            private static final int TYPE_DISK = 0;
            private static final int TYPE_SEPARATOR = 1;




            @Override
            public int getItemViewType(int position) {
                if (position == 0) {
                    return TYPE_SEPARATOR;
                }
                if (position<=InstalledDisks.disks.size()) {
                    return TYPE_DISK;
                }
                if (position == InstalledDisks.disks.size() + 1) {
                    return TYPE_SEPARATOR;
                }
                return TYPE_DISK;
            }

            @Override
            public int getViewTypeCount() {
                return 2;
            }

            @Override
            public int getCount() {
                return 1+InstalledDisks.disks.size() + 1 + onlineDisks.size();
            }

            @Override
            public String getItem(int position) {
                return null;
            }

            @Override
            public long getItemId(int position) {
                return position;
            }

            public View getView(int position, View view, ViewGroup parent) {
                int rowType = getItemViewType(position);

                if (view == null) {
                    if (rowType == TYPE_DISK) {
                        view = getActivity().getLayoutInflater().inflate(R.layout.listitem_diskinfo, null);
                    } else {
                        view = getActivity().getLayoutInflater().inflate(R.layout.listitem_diskinfo_header, null);
                    }
                }

                if (rowType == TYPE_DISK) {
                    DiskInfo diskInfo = null;
                    if (position <= InstalledDisks.disks.size())  {
                        diskInfo = InstalledDisks.disks.get(position-1);
                    } else {
                        diskInfo = onlineDisks.get(position - (InstalledDisks.disks.size() + 2));
                    }
                    view.setTag(diskInfo);
                    ImageCache.getImage(view, R.id.image, diskInfo.coverUrl);
                    Utils.setText(view, R.id.title, diskInfo.title);
                    Utils.setText(view, R.id.subtitle, diskInfo.publisher);
                    //Utils.setVisibility(view, R.id.installed, (InstalledDisks.getByKey(diskInfo.key) == null) ? View.GONE : View.VISIBLE);
                } else {
                    Utils.setText(view, R.id.title, (position==0) ? "Installed" : "Online");
                }
                return view;
            }

        };

        //
        // Get the list of online disks
        //
        Network.DownloadJsonTask downloadTask;
        private void refreshOnlineList() {
            downloadTask = new Network.DownloadJsonTask() {
                @Override
                protected HttpUriRequest getHttpRequest() {
                    return new HttpGet(SERVER_ROOT + "beebdroid.json");
                }

                @Override
                protected void onDownloadJsonComplete(Object object) throws JSONException {
                    JSONArray a = (JSONArray)object;
                    onlineDisks.clear();
                    for (int i=0 ; i<a.length() ; i++) {
                        DiskInfo diskInfo = new DiskInfo(a.getJSONObject(i));
                        if (TextUtils.isEmpty(diskInfo.coverUrl)) {
                            diskInfo.coverUrl = SERVER_ROOT + diskInfo.key + ".jpg";
                        }
                        if (TextUtils.isEmpty(diskInfo.diskUrl)) {
                            diskInfo.diskUrl = SERVER_ROOT + diskInfo.key + ".zip";
                        }
                        if (InstalledDisks.getByKey(diskInfo.key) == null) {
                            onlineDisks.add(diskInfo);
                        }
                    }
                    adapter.notifyDataSetChanged();
                }
                @Override
                protected void onError(String errorText) {
                    Toast.makeText(getActivity(), errorText, Toast.LENGTH_SHORT).show();
                }
            };
            downloadTask.execute();
        }


        @Override
        public void onListItemClick(ListView l, View view, int position, long id) {
            DiskInfo diskInfo = (DiskInfo)view.getTag();
            if (null != InstalledDisks.getByKey(diskInfo.key)) {
                getActivity().setResult(ID_RESULT_LOADDISK);
                selectedDisk = (DiskInfo)view.getTag();
                getActivity().finish();
            } else {
                startDownload(diskInfo);
            }
        }

        private void startDownload(final DiskInfo onlineDiskInfo) {
            final File dir = App.app.getFilesDir();
            final File tmpFile = new File(dir, "tmp.bin");
            Network.DownloadBinaryTask task = new Network.DownloadBinaryTask(tmpFile.getAbsolutePath(), false) {
                @Override
                protected HttpUriRequest getHttpRequest() {
                    return new HttpGet(onlineDiskInfo.diskUrl);
                }

                @Override
                protected void onDownloadComplete() {
                    File targetFile = new File(dir, onlineDiskInfo.key);
                    if (contentType.equals("application/zip")) {
                        try {
                            Utils.unzip(tmpFile, targetFile, true);
                        } catch (IOException e) {
                            Toast.makeText(getActivity(), "Error processing zipped disk image: " + e.toString(), Toast.LENGTH_SHORT).show();
                            return;
                        }
                    }
                    else {
                        tmpFile.renameTo(targetFile);
                    }
                    Toast.makeText(getActivity(), "Installed OK!", Toast.LENGTH_SHORT).show();
                    getActivity().setResult(ID_RESULT_LOADDISK);
                    selectedDisk = InstalledDisks.add(onlineDiskInfo);
                    getActivity().finish();
                }
                @Override
                protected void onError(String errorText) {
                    Toast.makeText(getActivity(), errorText, Toast.LENGTH_SHORT).show();
                }

            };
            task.execute();
        }
    }

	/*
	 * ACTIVITY MANAGEMENT (i.e. functions generally called by the OS)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_loaddisk);
        mTabHost = (FragmentTabHost) findViewById(android.R.id.tabhost);
        //mTabHost.setup();
        mTabHost.setup(this, getSupportFragmentManager(), android.R.id.tabcontent);
        //mTabHost.setOnTabChangedListener(this);
        //setupTab("Recent", installedAdapter, R.layout.listview, onInstalledItemClickListener);
        //setupTab("Online", onlineAdapter, R.layout.listview_online, onOnlineItemClickListener);
		//setupTab("Local", localAdapter, R.layout.listview, onInstalledItemClickListener);

        ListFragment frag;

        mTabHost.addTab(
                mTabHost.newTabSpec("games").setIndicator("Games", null),
                GamesListFragment.class, null);

        Bundle b = new Bundle();
        b.putBoolean(FilePickerFragment.KEY_ALLOW_DIR_CREATE, false);
        b.putBoolean(FilePickerFragment.KEY_ALLOW_MULTIPLE, false);
        b.putInt(FilePickerFragment.KEY_MODE, FilePickerFragment.MODE_FILE);

        mTabHost.addTab(
                mTabHost.newTabSpec("local").setIndicator("Local", null),
                FilePickerFragment.class, b);

        //TabSpec spec = mTabHost.newTabSpec(tag);


        //setupTab("Saved", savedAdapter, R.layout.listview, onSaveItemClickListener);
        //int tab = getIntent().getIntExtra("startTab", 0);
       // mTabHost.setCurrentTab(tab);
        if (SavedGameInfo.savedGames == null) {
        	SavedGameInfo.init(this);
        }
     
	}

	public static DiskInfo selectedDisk;
	


	
	/*
	 * SAVED GAMES ADAPTER
	 */
	BaseAdapter savedAdapter = new BaseAdapter() {
		
		@Override
		public int getCount() {
			return SavedGameInfo.savedGames.size() + 1;
		}
		@Override
		public Object getItem(int position) {
			return null;
		}
	
		@Override
		public long getItemId(int position) {
			return position;
		}
	
		@Override
		public View getView(int position, View view, ViewGroup parent) {
			if (position == 0) {
				return getLayoutInflater().inflate(R.layout.listitem_add_new_saved_game, null);
			}
			position--;
			SavedGameInfo info = SavedGameInfo.savedGames.get(position);
			if (view == null || view.getTag()==null) {
				view = getLayoutInflater().inflate(R.layout.listitem_savedgame, null);
			}
			view.setTag(position);
			
			ImageView img = (ImageView)view.findViewById(R.id.image);
			img.setBackgroundDrawable(new BitmapDrawable(info.thumbnail));
			//img.setImageBitmap(info.thumbnail);
			
			Utils.setText(view, R.id.title, (info.diskInfo==null)? "No disk" : info.diskInfo.title);
			Utils.setText(view, R.id.age, Utils.age(info.timestamp) + " ");
	
			// Expand load/save buttons area
			View buttons = view.findViewById(R.id.buttons);
			if (info.equals(SavedGameInfo.current)) {
				buttons.setVisibility(View.VISIBLE);
				view.setBackgroundColor(0xffd0d0ff);
				buttons.findViewById(R.id.btnRestore).setTag(position);
				buttons.findViewById(R.id.btnOverwrite).setTag(position);
			}
			else {
				buttons.setVisibility(View.GONE);	
				view.setBackgroundDrawable(null);
			}
			return view;
		}
		
	};

	
	OnItemClickListener onOnlineItemClickListener = new OnItemClickListener() {
		@Override
		public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
		}
	};
	OnItemClickListener onSaveItemClickListener = new OnItemClickListener() {
		@Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            if (position == 0) {
				doSave(-1);
				return;
			}
			doRestore(position - 1);		
		}
	};
	

	

	
	//
	// Saved states
	//
	/*@Override
	public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
		menu.add(0, ID_DELETE, 0, "Delete");
	}
	@Override
	public boolean onContextItemSelected (MenuItem item) {
		AdapterView.AdapterContextMenuInfo info = (AdapterView.AdapterContextMenuInfo)item.getMenuInfo();
		SavedGameInfo.delete(this, info.position-1);
		adapter.notifyDataSetChanged();
		return true;
	}*/
	
	public void onOverwriteClicked(View view) {
		doSave((Integer)view.getTag());
	}
	public void onRestoreClicked(View view) {
		doRestore((Integer)view.getTag());
	}
	private void doSave(int index) {
		Intent data = new Intent();
		data.putExtra("index", index);
		setResult(ID_RESULT_SAVE, data);
		finish();
	}
	private void doRestore(int index) {
		Intent data = new Intent();
		data.putExtra("index", index);
		setResult(ID_RESULT_RESTORE, data);
		finish();
	}


    public void onCloseClicked(View view) {
        setResult(Activity.RESULT_CANCELED);
        finish();
    }

    public  void onFilePicked(Uri file) {
        DiskInfo localDisk = new DiskInfo();
        localDisk.key = file.toString();
        LoadDisk.selectedDisk = localDisk;
        setResult(ID_RESULT_LOADDISK);
        finish();
    }

}