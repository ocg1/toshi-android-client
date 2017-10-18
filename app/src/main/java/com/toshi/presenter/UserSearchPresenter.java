/*
 * 	Copyright (c) 2017. Toshi Inc
 *
 * 	This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.toshi.presenter;

import android.content.Intent;
import android.support.v4.content.ContextCompat;
import android.support.v7.widget.DefaultItemAnimator;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.view.View;

import com.jakewharton.rxbinding.widget.RxTextView;
import com.toshi.BuildConfig;
import com.toshi.R;
import com.toshi.model.local.Contact;
import com.toshi.model.local.User;
import com.toshi.util.KeyboardUtil;
import com.toshi.util.LogUtil;
import com.toshi.util.OnSingleClickListener;
import com.toshi.util.UserSearchType;
import com.toshi.view.BaseApplication;
import com.toshi.view.activity.ChatActivity;
import com.toshi.view.activity.GroupParticipantsActivity;
import com.toshi.view.activity.UserSearchActivity;
import com.toshi.view.activity.ViewUserActivity;
import com.toshi.view.adapter.ContactsAdapter;
import com.toshi.view.adapter.listeners.OnItemClickListener;
import com.toshi.view.custom.HorizontalLineDivider;

import java.util.List;
import java.util.concurrent.TimeUnit;

import rx.Single;
import rx.Subscription;
import rx.android.schedulers.AndroidSchedulers;
import rx.observables.ConnectableObservable;
import rx.subscriptions.CompositeSubscription;

public final class UserSearchPresenter
        implements
            Presenter<UserSearchActivity>,
            OnItemClickListener<User> {

    private boolean firstTimeAttaching = true;
    private UserSearchActivity activity;
    private CompositeSubscription subscriptions;

    private @UserSearchType.Type int viewType;

    @Override
    public void onViewAttached(final UserSearchActivity activity) {
        this.activity = activity;
        if (this.firstTimeAttaching) {
            this.firstTimeAttaching = false;
            initLongLivingObjects();
        }

        initShortLivingObjects();
    }

    private void initLongLivingObjects() {
        this.subscriptions = new CompositeSubscription();
    }

    private void initShortLivingObjects() {
        processIntentData();
        initToolbar();
        initClickListeners();
        initSearch();
        initRecyclerView();
    }

    @SuppressWarnings("WrongConstant")
    private void processIntentData() {
        this.viewType = this.activity.getIntent().getIntExtra(UserSearchActivity.VIEW_TYPE, UserSearchType.PROFILE);
    }

    private void initToolbar() {
        final boolean isProfileType = this.viewType == UserSearchType.PROFILE;
        final String title = isProfileType
                ? this.activity.getString(R.string.search)
                : this.activity.getString(R.string.new_chat);
        this.activity.getBinding().title.setText(title);
        this.activity.getBinding().closeButton.setOnClickListener(this.handleCloseClicked);
        if (BuildConfig.DEBUG) {
            this.activity.getBinding().newGroup.setVisibility(isProfileType ? View.GONE : View.VISIBLE);
        }
    }

    private void initClickListeners() {
        this.activity.getBinding().clearButton.setOnClickListener(__ -> handleClearButtonClicked());
        if (this.viewType == UserSearchType.CONVERSATION) {
            this.activity.getBinding().newGroup.setOnClickListener(__ -> handleNewGroupClicked());
        }
    }

    private void handleClearButtonClicked() {
        if (this.activity == null) return;
        this.activity.getBinding().search.setText(null);
    }

    private void handleNewGroupClicked() {
        final Intent intent = new Intent(this.activity, GroupParticipantsActivity.class);
        this.activity.startActivity(intent);
    }

    private void initSearch() {
        final ConnectableObservable<String> sourceObservable =
                RxTextView
                .textChangeEvents(this.activity.getBinding().search)
                .skip(1)
                .debounce(500, TimeUnit.MILLISECONDS)
                .map(event -> event.text().toString())
                .publish();

        final Subscription searchSub =
                sourceObservable
                .filter(query -> query.length() > 2)
                .subscribe(
                        this::runSearchQuery,
                        this::handleSearchError
                );

        final Subscription uiSub =
                sourceObservable
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::updateSearchUi,
                        this::handleSearchError
                );

        final Subscription sourceSub = sourceObservable.connect();
        this.subscriptions.addAll(sourceSub, searchSub, uiSub);
    }

    private void runSearchQuery(final String query) {
        final Single<List<User>> userSingle =
                this.viewType == UserSearchType.PROFILE
                        ? searchContacts(query)
                        : searchOnlineUsers(query);

        final Subscription searchSub =
                userSingle
                .observeOn(AndroidSchedulers.mainThread())
                .subscribe(
                        this::handleSearchResult,
                        this::handleSearchError
                );

        this.subscriptions.add(searchSub);
    }

    private Single<List<User>> searchOnlineUsers(final String query) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .searchOnlineUsers(query);
    }

    private Single<List<User>> searchContacts(final String query) {
        return BaseApplication
                .get()
                .getRecipientManager()
                .searchContacts(query)
                .toObservable()
                .flatMapIterable(contacts -> contacts)
                .map(Contact::getUser)
                .toList()
                .toSingle();
    }

    private void handleSearchResult(final List<User> users) {
        final ContactsAdapter adapter = getContactsAdapter();
        if (adapter == null) return;
        adapter.setUsers(users);
    }

    private ContactsAdapter getContactsAdapter() {
        if (this.activity == null) return null;
        return (ContactsAdapter) this.activity.getBinding().searchResults.getAdapter();
    }

    private void updateSearchUi(final String query) {
        final ContactsAdapter adapter = getContactsAdapter();
        if (adapter == null) return;

        if (query.length() == 0) {
            this.activity.getBinding().clearButton.setVisibility(View.GONE);
            adapter.clear();
            return;
        }

        this.activity.getBinding().clearButton.setVisibility(View.VISIBLE);
    }

    private void handleSearchError(final Throwable throwable) {
        LogUtil.exception(getClass(), "Error while searching for user", throwable);
    }

    private void initRecyclerView() {
        final RecyclerView recyclerView = this.activity.getBinding().searchResults;
        final RecyclerView.LayoutManager layoutManager = new LinearLayoutManager(this.activity);
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        final ContactsAdapter adapter = new ContactsAdapter()
                .setOnItemClickListener(this);
        recyclerView.setAdapter(adapter);

        final int dividerLeftPadding = activity.getResources().getDimensionPixelSize(R.dimen.avatar_size_small)
                + activity.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin)
                + activity.getResources().getDimensionPixelSize(R.dimen.list_item_avatar_margin);
        final int dividerRightPadding = activity.getResources().getDimensionPixelSize(R.dimen.activity_horizontal_margin);
        final HorizontalLineDivider lineDivider =
                new HorizontalLineDivider(ContextCompat.getColor(this.activity, R.color.divider))
                        .setRightPadding(dividerRightPadding)
                        .setLeftPadding(dividerLeftPadding);
        recyclerView.addItemDecoration(lineDivider);
    }

    private final OnSingleClickListener handleCloseClicked = new OnSingleClickListener() {
        @Override
        public void onSingleClick(final View v) {
            KeyboardUtil.hideKeyboard(v);
            activity.onBackPressed();
        }
    };

    @Override
    public void onItemClick(final User clickedUser) {
        if (this.viewType == UserSearchType.PROFILE) {
            goToProfileActivity(clickedUser);
        } else {
            goToChatActivity(clickedUser);
        }
    }

    private void goToProfileActivity(final User user) {
        final Intent intent = new Intent(this.activity, ViewUserActivity.class)
                .putExtra(ViewUserActivity.EXTRA__USER_ADDRESS, user.getToshiId());
        goToActivity(intent);
    }

    private void goToChatActivity(final User user) {
        final Intent intent = new Intent(this.activity, ChatActivity.class)
                .putExtra(ChatActivity.EXTRA__THREAD_ID, user.getToshiId());
        goToActivity(intent);
    }

    private void goToActivity(final Intent intent) {
        this.activity.startActivity(intent);
        this.activity.finish();
    }

    @Override
    public void onViewDetached() {
        this.subscriptions.clear();
        this.activity = null;
    }

    @Override
    public void onDestroyed() {
        this.subscriptions = null;
        this.activity = null;
    }
}
