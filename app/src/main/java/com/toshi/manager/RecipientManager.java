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

package com.toshi.manager;


import com.toshi.manager.network.IdService;
import com.toshi.manager.store.BlockedUserStore;
import com.toshi.manager.store.ContactStore;
import com.toshi.manager.store.GroupStore;
import com.toshi.manager.store.UserStore;
import com.toshi.model.local.BlockedUser;
import com.toshi.model.local.Contact;
import com.toshi.model.local.Group;
import com.toshi.model.local.Report;
import com.toshi.model.local.User;
import com.toshi.model.network.ServerTime;
import com.toshi.model.network.UserSearchResults;
import com.toshi.util.LogUtil;
import com.toshi.view.BaseApplication;

import java.io.IOException;
import java.util.List;

import rx.Completable;
import rx.Observable;
import rx.Single;
import rx.schedulers.Schedulers;

public class RecipientManager {

    private ContactStore contactStore;
    private GroupStore groupStore;
    private UserStore userStore;
    private BlockedUserStore blockedUserStore;

    /* package */ RecipientManager() {
        initDatabases();
    }

    private void initDatabases() {
        this.contactStore = new ContactStore();
        this.groupStore = new GroupStore();
        this.userStore = new UserStore();
        this.blockedUserStore = new BlockedUserStore();
    }

    public Single<User> getUserFromUsername(final String username) {
        // It's the same endpoint
        return getUserFromToshiId(username);
    }

    public Single<Group> getGroupFromId(final String id) {
        return this.groupStore.loadForId(id)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnError(t -> LogUtil.exception(getClass(), "getGroupFromId", t));
    }

    public Single<User> getUserFromToshiId(final String toshiId) {
        return Single
                .concat(
                        this.userStore.loadForToshiId(toshiId).toSingle(),
                        this.fetchAndCacheFromNetworkByToshiId(toshiId))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .first(this::isUserFresh)
                .doOnError(t -> LogUtil.exception(getClass(), "getUserFromToshiId", t))
                .toSingle();
    }

    private boolean isUserFresh(final User user) {
        if (user == null) return false;
        if (!BaseApplication.get().isConnected()) return true;
        return !user.needsRefresh();
    }

    public Single<User> getUserFromPaymentAddress(final String paymentAddress) {
        return Single
                .concat(
                        Single.just(userStore.loadForPaymentAddress(paymentAddress)),
                        this.fetchAndCacheFromNetworkByPaymentAddress(paymentAddress).toSingle()
                )
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .first(user -> user != null && !user.needsRefresh())
                .doOnError(t -> LogUtil.exception(getClass(), "getUserFromPaymentAddress", t))
                .toSingle();
    }

    private Single<User> fetchAndCacheFromNetworkByToshiId(final String userAddress) {
        return IdService
                .getApi()
                .getUser(userAddress)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnSuccess(this::cacheUser);
    }

    private Observable<User> fetchAndCacheFromNetworkByPaymentAddress(final String paymentAddress) {
        return IdService
                .getApi()
                .searchByPaymentAddress(paymentAddress)
                .toObservable()
                .filter(userSearchResults -> userSearchResults.getResults().size() > 0)
                .map(userSearchResults -> userSearchResults.getResults().get(0))
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io())
                .doOnNext(this::cacheUser)
                .doOnError(t -> LogUtil.exception(getClass(), "fetchAndCacheFromNetworkByPaymentAddress", t));
    }

    private void cacheUser(final User user) {
        this.userStore.save(user);
    }

    public Single<List<Contact>> loadAllContacts() {
        return this.contactStore
                .loadAll()
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io());
    }

    public Single<List<User>> searchOfflineUsers(final String query) {
        return this.userStore
                .queryUsername(query)
                .subscribeOn(Schedulers.io())
                .observeOn(Schedulers.io());
    }

    public Single<List<User>> searchOnlineUsers(final String query) {
        return IdService
                .getApi()
                .searchByUsername(query)
                .subscribeOn(Schedulers.io())
                .map(UserSearchResults::getResults);
    }

    public Single<Boolean> isUserAContact(final User user) {
        return this.contactStore.userIsAContact(user);
    }

    public Completable deleteContact(final User user) {
        return this.contactStore.delete(user);
    }

    public Completable saveContact(final User user) {
        return this.contactStore.save(user);
    }

    public Single<Boolean> isUserBlocked(final String ownerAddress) {
        return this.blockedUserStore
                .isBlocked(ownerAddress)
                .subscribeOn(Schedulers.io());
    }

    public Completable blockUser(final String ownerAddress) {
        final BlockedUser blockedUser = new BlockedUser()
                .setOwnerAddress(ownerAddress);
        return Completable.fromAction(() ->
                this.blockedUserStore.save(blockedUser))
                .subscribeOn(Schedulers.io());
    }

    public Completable unblockUser(final String ownerAddress) {
        return Completable.fromAction(() ->
                this.blockedUserStore.delete(ownerAddress))
                .subscribeOn(Schedulers.io());
    }

    public Single<Void> reportUser(final Report report) {
        return getTimestamp()
                .flatMap(serverTime ->
                        IdService
                        .getApi()
                        .reportUser(report, serverTime.get())
                )
                .subscribeOn(Schedulers.io());
    }

    public Single<ServerTime> getTimestamp() {
        return IdService
                .getApi()
                .getTimestamp();
    }

    public void clear() {
        this.userStore.clear();
        clearCache();
    }

    private void clearCache() {
        try {
            IdService
                    .get()
                    .clearCache();
        } catch (IOException e) {
            LogUtil.exception(getClass(), "Error while clearing network cache", e);
        }
    }
}
