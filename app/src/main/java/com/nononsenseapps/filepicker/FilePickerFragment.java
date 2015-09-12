/*
 * Copyright (c) 2015 Jonas Kalderstam
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nononsenseapps.filepicker;

import android.app.Activity;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.LoaderManager;
import android.Manifest;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.FileObserver;
import android.support.annotation.NonNull;
import android.support.v4.content.AsyncTaskLoader;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.Loader;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.support.v7.widget.util.SortedListAdapterCallback;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;
import android.widget.Toast;

import com.littlefluffytoys.beebdroid.R;


import java.io.File;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;


public class FilePickerFragment extends Fragment
        implements LoaderManager.LoaderCallbacks<SortedList<File>>,
        NewItemFragment.OnNewFolderListener, LogicHandler<File> {

    // The different preset modes of operation. This impacts the behaviour
    // and possible actions in the UI.
    public static final int MODE_FILE = 0;
    public static final int MODE_DIR = 1;
    public static final int MODE_FILE_AND_DIR = 2;
    // Where to display on open.
    public static final String KEY_START_PATH = "KEY_START_PATH";
    // See MODE_XXX constants above for possible values
    public static final String KEY_MODE = "KEY_MODE";
    // If it should be possible to create directories.
    public static final String KEY_ALLOW_DIR_CREATE = "KEY_ALLOW_DIR_CREATE";
    // Allow multiple items to be selected.
    public static final String KEY_ALLOW_MULTIPLE = "KEY_ALLOW_MULTIPLE";
    // Used for saving state.
    protected static final String KEY_CURRENT_PATH = "KEY_CURRENT PATH";
    protected final HashSet<File> mCheckedItems;
    protected int mode = MODE_FILE;
    protected File mCurrentPath = null;
    protected boolean allowCreateDir = false;
    protected boolean allowMultiple = false;
    protected OnFilePickedListener mListener;
    protected FileItemAdapter<File> mAdapter = null;
    protected TextView mCurrentDirView;
    protected SortedList<File> mFiles = null;
    protected Toast mToast = null;
    protected static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    public FilePickerFragment() {
            mCheckedItems = new HashSet<>();

            // Retain this fragment across configuration changes, to allow
            // asynctasks and such to be used with ease.
            setRetainInstance(true);
        }

        protected FileItemAdapter<File> getAdapter() {
            return mAdapter;
        }

        protected FileItemAdapter<File> getDummyAdapter() {
            return new FileItemAdapter<>(this);
        }

        /**
         * Set before making the fragment visible. This method will re-use the existing
         * arguments bundle in the fragment if it exists so extra arguments will not
         * be overwritten. This allows you to set any extra arguments in the fragment
         * constructor if you wish.
         * <p/>
         * The key/value-pairs listed below will be overwritten however.
         *
         * @param startPath      path to directory the picker will show upon start
         * @param mode           what is allowed to be selected (dirs, files, both)
         * @param allowMultiple  selecting a single item or several?
         * @param allowDirCreate can new directories be created?
         */
    public void setArgs(final String startPath, final int mode,
                        final boolean allowMultiple, final boolean allowDirCreate) {
        // There might have been arguments set elsewhere, if so do not overwrite them.
        Bundle b = getArguments();
        if (b == null) {
            b = new Bundle();
        }

        if (startPath != null) {
            b.putString(KEY_START_PATH, startPath);
        }
        b.putBoolean(KEY_ALLOW_DIR_CREATE, allowDirCreate);
        b.putBoolean(KEY_ALLOW_MULTIPLE, allowMultiple);
        b.putInt(KEY_MODE, mode);
        setArguments(b);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.nnf_fragment_filepicker, container, false);

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.nnf_picker_toolbar);
        if (toolbar != null) {
            setupToolbar(toolbar);
        }

        RecyclerView mRecyclerView = (RecyclerView) view.findViewById(android.R.id.list);
        // improve performance if you know that changes in content
        // do not change the size of the RecyclerView
        mRecyclerView.setHasFixedSize(true);
        // use a linear layout manager
        LinearLayoutManager mLayoutManager = new LinearLayoutManager(getActivity());
        mRecyclerView.setLayoutManager(mLayoutManager);
        // Set adapter
        mAdapter = new FileItemAdapter<>(this);
        mRecyclerView.setAdapter(mAdapter);


        mCurrentDirView = (TextView) view.findViewById(R.id.nnf_current_dir);
        // Restore state
        if (mCurrentPath != null && mCurrentDirView != null) {
            mCurrentDirView.setText(getFullPath(mCurrentPath));
        }

        return view;
    }



    /**
     * Called when the ok-button is pressed.
     *
     * @param view which was clicked. Not used in default implementation.
     */
    public void onClickOk(View view) {
        if (mListener == null) {
            return;
        }

        // Some invalid cases first
        if ((allowMultiple || mode == MODE_FILE) && mCheckedItems.isEmpty()) {
            if (mToast == null) {
                mToast = Toast.makeText(getActivity(), R.string.nnf_select_something_first,
                        Toast.LENGTH_SHORT);
            }
            mToast.show();
            return;
        }

        if (mode == MODE_FILE) {
            mListener.onFilePicked(toUri(getFirstCheckedItem()));
        } else if (mode == MODE_DIR) {
            mListener.onFilePicked(toUri(mCurrentPath));
        } else {
            // single FILE OR DIR
            if (mCheckedItems.isEmpty()) {
                mListener.onFilePicked(toUri(mCurrentPath));
            } else {
                mListener.onFilePicked(toUri(getFirstCheckedItem()));
            }
        }
    }

    /**
     * Configure the toolbar anyway you like here. Default is to set it as the activity's
     * main action bar. Override if you already provide an action bar.
     * Not called if no toolbar was found.
     *
     * @param toolbar from layout with id "picker_toolbar"
     */
    protected void setupToolbar(Toolbar toolbar) {
        ((AppCompatActivity) getActivity()).setSupportActionBar(toolbar);
    }

    public File getFirstCheckedItem() {
        //noinspection LoopStatementThatDoesntLoop
        for (File file : mCheckedItems) {
            return file;
        }
        return null;
    }

    protected List<Uri> toUri(Iterable<File> files) {
        ArrayList<Uri> uris = new ArrayList<>();
        for (File file : files) {
            uris.add(toUri(file));
        }
        return uris;
    }

    public boolean isCheckable(final File data) {
        final boolean checkable;
        if (isDir(data)) {
            checkable = ((mode == MODE_DIR && allowMultiple) ||
                    (mode == MODE_FILE_AND_DIR && allowMultiple));
        } else {
            // File
            checkable = (mode != MODE_DIR);
        }
        return checkable;
    }

    @Override
    public void onAttach(Activity context) {
        super.onAttach(context);
        try {
            mListener = (OnFilePickedListener) context;
        } catch (ClassCastException e) {
            throw new ClassCastException(context.toString() +
                    " must implement OnFilePickedListener");
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setHasOptionsMenu(true);
    }

    /**
     * Called when the fragment's activity has been created and this
     * fragment's view hierarchy instantiated.  It can be used to do final
     * initialization once these pieces are in place, such as retrieving
     * views or restoring state.  It is also useful for fragments that use
     * {@link #setRetainInstance(boolean)} to retain their instance,
     * as this callback tells the fragment when it is fully associated with
     * the new activity instance.  This is called after {@link #onCreateView}
     * and before {@link #onViewStateRestored(Bundle)}.
     *
     * @param savedInstanceState If the fragment is being re-created from
     *                           a previous saved state, this is the state.
     */
    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // Only if we have no state
        if (mCurrentPath == null) {
            if (savedInstanceState != null) {
                mode = savedInstanceState.getInt(KEY_MODE, mode);
                allowCreateDir = savedInstanceState
                        .getBoolean(KEY_ALLOW_DIR_CREATE, allowCreateDir);
                allowMultiple = savedInstanceState
                        .getBoolean(KEY_ALLOW_MULTIPLE, allowMultiple);
                mCurrentPath =
                        getPath(savedInstanceState.getString(KEY_CURRENT_PATH));
            } else if (getArguments() != null) {
                mode = getArguments().getInt(KEY_MODE, mode);
                allowCreateDir = getArguments()
                        .getBoolean(KEY_ALLOW_DIR_CREATE, allowCreateDir);
                allowMultiple = getArguments()
                        .getBoolean(KEY_ALLOW_MULTIPLE, allowMultiple);
                if (getArguments().containsKey(KEY_START_PATH)) {
                    mCurrentPath =
                            getPath(getArguments().getString(KEY_START_PATH));
                }
            }

            // If still null
            if (mCurrentPath == null) {
                mCurrentPath = getRoot();
            }
        }

        refresh();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.picker_actions, menu);

        MenuItem item = menu.findItem(R.id.nnf_action_createdir);
        item.setVisible(allowCreateDir);
    }



    @Override
    public void onSaveInstanceState(Bundle b) {
        super.onSaveInstanceState(b);
        b.putString(KEY_CURRENT_PATH, mCurrentPath.toString());
        b.putBoolean(KEY_ALLOW_MULTIPLE, allowMultiple);
        b.putBoolean(KEY_ALLOW_DIR_CREATE, allowCreateDir);
        b.putInt(KEY_MODE, mode);
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }

    /**
     * Refreshes the list. Call this when current path changes. This method also checks
     * if permissions are granted and requests them if necessary. See hasPermission()
     * and handlePermission(). By default, these methods do nothing. Override them if
     * you need to request permissions at runtime.
     */
    protected void refresh() {
        if (hasPermission()) {
            getLoaderManager()
                    .restartLoader(0, null, FilePickerFragment.this);
        } else {
            handlePermission();
        }
    }

    /**
     * Instantiate and return a new Loader for the given ID.
     *
     * @param id   The ID whose loader is to be created.
     * @param args Any arguments supplied by the caller.
     * @return Return a new Loader instance that is ready to start loading.
     */
    @Override
    public Loader<SortedList<File>> onCreateLoader(final int id, final Bundle args) {
        return getLoader();
    }

    /**
     * Called when a previously created loader has finished its load.
     *
     * @param loader The Loader that has finished.
     * @param data   The data generated by the Loader.
     */
    @Override
    public void onLoadFinished(final Loader<SortedList<File>> loader,
                               final SortedList<File> data) {
        mCheckedItems.clear();
        mFiles = data;
        mAdapter.setList(data);
        if (mCurrentDirView != null) {
            mCurrentDirView.setText(getFullPath(mCurrentPath));
        }
    }

    /**
     * Called when a previously created loader is being reset, and thus
     * making its data unavailable.  The application should at this point
     * remove any references it has to the Loader's data.
     *
     * @param loader The Loader that is being reset.
     */
    @Override
    public void onLoaderReset(final Loader<SortedList<File>> loader) {
        mAdapter.setList(null);
        mFiles = null;
    }

    /**
     * @param position 0 - n, where the header has been subtracted
     * @param data     the actual file or directory
     * @return an integer greater than 0
     */
    @Override
    public int getItemViewType(int position, File data) {
        if (isCheckable(data)) {
            return LogicHandler.VIEWTYPE_CHECKABLE;
        } else {
            return LogicHandler.VIEWTYPE_DIR;
        }
    }


    /**
     * @param parent   Containing view
     * @param viewType which the ViewHolder will contain
     * @return a view holder for a file or directory
     */
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
        View v;
        switch (viewType) {
            case LogicHandler.VIEWTYPE_HEADER:
                v = LayoutInflater.from(getActivity()).inflate(R.layout.nnf_filepicker_listitem_dir,
                        parent, false);
                return new HeaderViewHolder(v);
            case LogicHandler.VIEWTYPE_CHECKABLE:
                v = LayoutInflater.from(getActivity()).inflate(R.layout.nnf_filepicker_listitem_checkable,
                        parent, false);
                return new CheckableViewHolder(v);
            case LogicHandler.VIEWTYPE_DIR:
            default:
                v = LayoutInflater.from(getActivity()).inflate(R.layout.nnf_filepicker_listitem_dir,
                        parent, false);
                return new DirViewHolder(v);
        }
    }

    public void onBindHeaderViewHolder(HeaderViewHolder vh) {
        vh.text.setText("..");
    }

        /**
         * @param vh       to bind data from either a file or directory
         * @param position 0 - n, where the header has been subtracted
         * @param data     the file or directory which this item represents
         */
    public void onBindViewHolder(DirViewHolder vh, int position, File data) {
        vh.file = data;
        vh.icon.setVisibility(isDir(data) ? View.VISIBLE : View.GONE);
        vh.text.setText(getName(data));
    }

    /**
     * Animate de-selection of visible views and clear
     * selected set.
     */
    public void clearSelections() {
        mCheckedItems.clear();
    }


    /**
     * Called when a header item ("..") is clicked.
     *
     * @param view       that was clicked. Not used in default implementation.
     * @param viewHolder for the clicked view
     */
    public void onClickHeader(View view, HeaderViewHolder viewHolder) {
        goUp();
    }

    /**
     * Browses to the parent directory from the current directory. For example, if the current
     * directory is /foo/bar/, then goUp() will change the current directory to /foo/. It is up to
     * the caller to not call this in vain, e.g. if you are already at the root.
     * <p/>
     * Currently selected items are cleared by this operation.
     */
    public void goUp() {
        goToDir(getParent(mCurrentPath));
    }

    /**
     * Called when a non-selectable item, typically a directory, is clicked.
     *
     * @param view       that was clicked. Not used in default implementation.
     * @param viewHolder for the clicked view
     */
    public void onClickDir(View view, DirViewHolder viewHolder) {
        if (isDir(viewHolder.file)) {
            goToDir(viewHolder.file);
        }
    }

    /**
     * Browses to the designated directory. It is up to the caller verify that the argument is
     * in fact a directory.
     * <p/>
     * Currently selected items are cleared by this operation.
     *
     * @param file representing the target directory.
     */
    public void goToDir(@NonNull File file) {
        mCurrentPath = file;
        mCheckedItems.clear();
        refresh();
    }

    /**
     * Long clicking a non-selectable item does nothing by default.
     *
     * @param view       which was long clicked. Not used in default implementation.
     * @param viewHolder for the clicked view
     * @return true if the callback consumed the long click, false otherwise.
     */
    public boolean onLongClickDir(View view, DirViewHolder viewHolder) {
        return false;
    }

    /**
     * Called when a selectable item is clicked. This might be either a file or a directory.
     *
     * @param view       that was clicked. Not used in default implementation.
     * @param viewHolder for the clicked view
     */
    public void onClickCheckable(View view, CheckableViewHolder viewHolder) {
        if (isDir(viewHolder.file)) {
            goToDir(viewHolder.file);
        } else {
            mListener.onFilePicked(toUri(viewHolder.file));
        }
    }

    /**
     * Long clicking a selectable item should toggle its selected state. Note that if only a
     * single item can be selected, then other potentially selected views on screen must be
     * de-selected.
     *
     * @param view       which was long clicked. Not used in default implementation.
     * @param viewHolder for the clicked view
     * @return true if the callback consumed the long click, false otherwise.
     */
    public boolean onLongClickCheckable(View view, CheckableViewHolder viewHolder) {
        return true;
    }



    /**
     * This interface must be implemented by activities that contain this
     * fragment to allow an interaction in this fragment to be communicated
     * to the activity and potentially other fragments contained in that
     * activity.
     * <p/>
     * See the Android Training lesson <a href=
     * "http://developer.android.com/training/basics/fragments/communicating
     * .html"
     * >Communicating with Other Fragments</a> for more information.
     */
    public interface OnFilePickedListener {
        void onFilePicked(Uri file);
    }

    public class HeaderViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener {
        final TextView text;

        public HeaderViewHolder(View v) {
            super(v);
            v.setOnClickListener(this);
            text = (TextView) v.findViewById(android.R.id.text1);
        }

        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        @Override
        public void onClick(View v) {
            onClickHeader(v, this);
        }
    }

    public class DirViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener, View.OnLongClickListener {

        public View icon;
        public TextView text;
        public File file;

        public DirViewHolder(View v) {
            super(v);
            v.setOnClickListener(this);
            v.setOnLongClickListener(this);
            icon = v.findViewById(R.id.item_icon);
            text = (TextView) v.findViewById(android.R.id.text1);
        }

        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        @Override
        public void onClick(View v) {
            onClickDir(v, this);
        }

        /**
         * Called when a view has been clicked and held.
         *
         * @param v The view that was clicked and held.
         * @return true if the callback consumed the long click, false otherwise.
         */
        @Override
        public boolean onLongClick(View v) {
            return onLongClickDir(v, this);
        }
    }

    public class CheckableViewHolder extends DirViewHolder {

        public CheckableViewHolder(View v) {
            super(v);
        }

        /**
         * Called when a view has been clicked.
         *
         * @param v The view that was clicked.
         */
        @Override
        public void onClick(View v) {
            onClickCheckable(v, this);
        }

        /**
         * Called when a view has been clicked and held.
         *
         * @param v The view that was clicked and held.
         * @return true if the callback consumed the long click, false otherwise.
         */
        @Override
        public boolean onLongClick(View v) {
            return onLongClickCheckable(v, this);
        }
    }

    /**
     * @return true if app has been granted permission to write to the SD-card.
     */
    protected boolean hasPermission() {
        return PackageManager.PERMISSION_GRANTED ==
                ContextCompat.checkSelfPermission(getActivity(),
                        Manifest.permission.WRITE_EXTERNAL_STORAGE);
    }

    /**
     * Request permission to write to the SD-card.
     */
    protected void handlePermission() {
//         Should we show an explanation?
//        if (shouldShowRequestPermissionRationale(
//                Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
//             Explain to the user why we need permission
//        }

        requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
    }

    /**
     * This the method that gets notified when permission is granted/denied. By default,
     * a granted request will result in a refresh of the list.
     *
     * @param requestCode  the code you requested
     * @param permissions  array of permissions you requested. empty if process was cancelled.
     * @param grantResults results for requests. empty if process was cancelled.
     */
    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        // If arrays are empty, then process was cancelled
        if (permissions.length == 0) {
        } else { // if (requestCode == PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE) {
            if (PackageManager.PERMISSION_GRANTED == grantResults[0]) {
                // Do refresh
                refresh();
            } else {
                Toast.makeText(getContext(), R.string.nnf_permission_external_write_denied,
                        Toast.LENGTH_SHORT).show();
            }
        }
    }

    /**
     * Return true if the path is a directory and not a file.
     *
     * @param path either a file or directory
     * @return true if path is a directory, false if file
     */
    @Override
    public boolean isDir(final File path) {
        return path.isDirectory();
    }

    /**
     * @param path either a file or directory
     * @return filename of path
     */
    @Override
    public String getName(File path) {
        return path.getName();
    }

    /**
     * Return the path to the parent directory. Should return the root if
     * from is root.
     *
     * @param from either a file or directory
     * @return the parent directory
     */
    @Override
    public File getParent(final File from) {
        if (from.getPath().equals(getRoot().getPath())) {
            // Already at root, we can't go higher
            return from;
        } else if (from.getParentFile() != null) {
            if (from.isFile()) {
                return getParent(from.getParentFile());
            } else {
                return from.getParentFile();
            }
        } else {
            return from;
        }
    }

    /**
     * Convert the path to the type used.
     *
     * @param path either a file or directory
     * @return File representation of the string path
     */
    @Override
    public File getPath(final String path) {
        return new File(path);
    }

    /**
     * @param path either a file or directory
     * @return the full path to the file
     */
    @Override
    public String getFullPath(final File path) {
        return path.getPath();
    }

    /**
     * Get the root path.
     *
     * @return the highest allowed path, which is "/" by default
     */
    @Override
    public File getRoot() {
        return new File("/");
    }

    /**
     * Convert the path to a URI for the return intent
     *
     * @param file either a file or directory
     * @return a Uri
     */
    @Override
    public Uri toUri(final File file) {
        return Uri.fromFile(file);
    }

    /**
     * Get a loader that lists the Files in the current path,
     * and monitors changes.
     */
    @Override
    public Loader<SortedList<File>> getLoader() {
        return new AsyncTaskLoader<SortedList<File>>(getActivity()) {

            FileObserver fileObserver;

            @Override
            public SortedList<File> loadInBackground() {
                File[] listFiles = mCurrentPath.listFiles();
                final int initCap = listFiles == null ? 0 : listFiles.length;

                SortedList<File> files = new SortedList<>(File.class, new SortedListAdapterCallback<File>(getDummyAdapter()) {
                    @Override
                    public int compare(File lhs, File rhs) {
                        return compareFiles(lhs, rhs);
                    }

                    @Override
                    public boolean areContentsTheSame(File file, File file2) {
                        return file.getAbsolutePath().equals(file2.getAbsolutePath()) && (file.isFile() == file2.isFile());
                    }

                    @Override
                    public boolean areItemsTheSame(File file, File file2) {
                        return areContentsTheSame(file, file2);
                    }
                }, initCap);


                files.beginBatchedUpdates();
                if (listFiles != null) {
                    for (java.io.File f : listFiles) {
                        if (isItemVisible(f)) {
                            files.add(f);
                        }
                    }
                }
                files.endBatchedUpdates();

                return files;
            }

            /**
             * Handles a request to start the Loader.
             */
            @Override
            protected void onStartLoading() {
                super.onStartLoading();

                // handle if directory does not exist. Fall back to root.
                if (mCurrentPath == null || !mCurrentPath.isDirectory()) {
                    mCurrentPath = getRoot();
                }

                // Start watching for changes
                fileObserver = new FileObserver(mCurrentPath.getPath(),
                        FileObserver.CREATE |
                                FileObserver.DELETE
                                | FileObserver.MOVED_FROM | FileObserver.MOVED_TO
                ) {

                    @Override
                    public void onEvent(int event, String path) {
                        // Reload
                        onContentChanged();
                    }
                };
                fileObserver.startWatching();

                forceLoad();
            }

            /**
             * Handles a request to completely reset the Loader.
             */
            @Override
            protected void onReset() {
                super.onReset();

                // Stop watching
                if (fileObserver != null) {
                    fileObserver.stopWatching();
                    fileObserver = null;
                }
            }
        };
    }

    /**
     * Name is validated to be non-null, non-empty and not containing any
     * slashes.
     *
     * @param name The name of the folder the user wishes to create.
     */
    @Override
    public void onNewFolder(final String name) {
        File folder = new File(mCurrentPath, name);

        if (folder.mkdir()) {
            mCurrentPath = folder;
            refresh();
        } else {
            Toast.makeText(getActivity(), R.string.nnf_create_folder_error,
                    Toast.LENGTH_SHORT).show();
        }
    }

    /**
     * Used by the list to determine whether a file should be displayed or not.
     * Default behavior is to always display folders. If files can be selected,
     * then files are also displayed. Override this method to enable other
     * filtering behaviour, like only displaying files with specific extensions (.zip, .txt, etc).
     *
     * @param file to maybe add. Can be either a directory or file.
     * @return True if item should be added to the list, false otherwise
     */
    protected boolean isItemVisible(final File file) {
        return isDir(file) || (mode == MODE_FILE || mode == MODE_FILE_AND_DIR);
    }

    /**
     * Compare two files to determine their relative sort order. This follows the usual
     * comparison interface. Override to determine your own custom sort order.
     * <p/>
     * Default behaviour is to place directories before files, but sort them alphabetically
     * otherwise.
     *
     * @param lhs File on the "left-hand side"
     * @param rhs File on the "right-hand side"
     * @return -1 if if lhs should be placed before rhs, 0 if they are equal,
     * and 1 if rhs should be placed before lhs
     */
    protected int compareFiles(File lhs, File rhs) {
        if (lhs.isDirectory() && !rhs.isDirectory()) {
            return -1;
        } else if (rhs.isDirectory() && !lhs.isDirectory()) {
            return 1;
        } else {
            return lhs.getName().compareToIgnoreCase(rhs.getName());
        }
    }
}
