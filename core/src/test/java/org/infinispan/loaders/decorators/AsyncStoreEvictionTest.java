/*
 * JBoss, Home of Professional Open Source
 * Copyright 2012 Red Hat Inc. and/or its affiliates and other contributors
 * as indicated by the @author tags. All rights reserved.
 * See the copyright.txt in the distribution for a
 * full listing of individual contributors.
 *
 * This copyrighted material is made available to anyone wishing to use,
 * modify, copy, or redistribute it subject to the terms and conditions
 * of the GNU Lesser General Public License, v. 2.1.
 * This program is distributed in the hope that it will be useful, but WITHOUT A
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE.  See the GNU Lesser General Public License for more details.
 * You should have received a copy of the GNU Lesser General Public License,
 * v.2.1 along with this distribution; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston,
 * MA  02110-1301, USA.
 */
package org.infinispan.loaders.decorators;

import java.util.concurrent.locks.ReentrantLock;

import org.infinispan.Cache;
import org.infinispan.configuration.cache.ConfigurationBuilder;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.eviction.EvictionStrategy;
import org.infinispan.loaders.CacheLoaderConfig;
import org.infinispan.loaders.CacheLoaderMetadata;
import org.infinispan.loaders.dummy.DummyInMemoryCacheStore;
import org.infinispan.test.CacheManagerCallable;
import org.infinispan.test.TestingUtil;
import org.infinispan.test.fwk.TestCacheManagerFactory;

import org.testng.annotations.Test;

@Test(groups = "unit", testName = "loaders.decorators.AsyncTest")
public class AsyncStoreEvictionTest {
   private final static ThreadLocal<LockableCacheStore> STORE = new ThreadLocal<LockableCacheStore>();

   public static class LockableCacheStoreConfig extends DummyInMemoryCacheStore.Cfg {
      private static final long serialVersionUID = 1L;

      public LockableCacheStoreConfig() {
         setCacheLoaderClassName(LockableCacheStore.class.getName());
      }
   }

   @CacheLoaderMetadata(configurationClass = LockableCacheStoreConfig.class)
   public static class LockableCacheStore extends DummyInMemoryCacheStore {
      private final ReentrantLock lock = new ReentrantLock();

      public LockableCacheStore() {
         super();
         STORE.set(this);
      }

      @Override
      public Class<? extends CacheLoaderConfig> getConfigurationClass() {
         return LockableCacheStoreConfig.class;
      }

      @Override
      public void store(InternalCacheEntry ed) {
         lock.lock();
         try {
            super.store(ed);
         } finally {
            lock.unlock();
         }
      }

      @Override
      public boolean remove(Object key) {
         lock.lock();
         try {
            return super.remove(key);
         } finally {
            lock.unlock();
         }
      }
   }
   private static abstract class OneEntryCacheManagerCallable extends CacheManagerCallable {
      protected final Cache<String, String> cache;
      protected final LockableCacheStore store;

      private static ConfigurationBuilder config(boolean passivation, int threads) {
         ConfigurationBuilder config = new ConfigurationBuilder();
         config.expiration().wakeUpInterval(100).eviction().strategy(EvictionStrategy.LRU).maxEntries(1).loaders().passivation(passivation).addStore()
               .cacheStore(new LockableCacheStore()).async().enable().threadPoolSize(threads);
         return config;
      }

      OneEntryCacheManagerCallable(boolean passivation, int threads) {
         super(TestCacheManagerFactory.createCacheManager(config(passivation, threads)));
         cache = cm.getCache();
         store = STORE.get();
      }
   }

   public void testEndToEndEvictionPassivation() throws Exception {
      testEndToEndEviction(true);
   }
   public void testEndToEndEviction() throws Exception {
      testEndToEndEviction(false);
   }
   private void testEndToEndEviction(boolean passivation) throws Exception {
      TestingUtil.withCacheManager(new OneEntryCacheManagerCallable(passivation, 1) {
         @Override
         public void call() {
            // simulate slow back end store
            store.lock.lock();
            try {
               cache.put("k1", "v1");
               cache.put("k2", "v2"); // force eviction of "k1"
               TestingUtil.sleepThread(100); // wait until the only AsyncProcessor thread is blocked
               cache.put("k3", "v3");
               cache.put("k4", "v4"); // force eviction of "k3"

               assert "v3".equals(cache.get("k3")) : "cache must return k3 == v3 (was: " + cache.get("k3") + ")";
            } finally {
               store.lock.unlock();
            }
         }
      });
   }

   public void testEndToEndUpdatePassivation() throws Exception {
      testEndToEndUpdate(true);
   }
   public void testEndToEndUpdate() throws Exception {
      testEndToEndUpdate(false);
   }
   private void testEndToEndUpdate(boolean passivation) throws Exception {
      TestingUtil.withCacheManager(new OneEntryCacheManagerCallable(passivation, 1) {
         @Override
         public void call() {
            cache.put("k1", "v0");
            cache.put("k2", "v2"); // force eviction of "k1"

            // wait for k1 == v1 to appear in store
            while (store.load("k1") == null)
               TestingUtil.sleepThread(10);

            // simulate slow back end store
            store.lock.lock();
            try {
               cache.put("k3", "v3");
               cache.put("k4", "v4"); // force eviction of "k3"
               TestingUtil.sleepThread(100); // wait until the only AsyncProcessor thread is blocked
               cache.put("k1", "v1");
               cache.put("k5", "v5"); // force eviction of "k1"

               assert "v1".equals(cache.get("k1")) : "cache must return k1 == v1 (was: " + cache.get("k1") + ")";
            } finally {
               store.lock.unlock();
            }
         }
      });
   }

   public void testEndToEndRemovePassivation() throws Exception {
      testEndToEndRemove(true);
   }
   public void testEndToEndRemove() throws Exception {
      testEndToEndRemove(false);
   }
   private void testEndToEndRemove(boolean passivation) throws Exception {
      TestingUtil.withCacheManager(new OneEntryCacheManagerCallable(passivation, 2) {
         @Override
         public void call() {
            cache.put("k1", "v1");
            cache.put("k2", "v2"); // force eviction of "k1"

            // wait for "k1" to appear in store
            while (store.load("k1") == null)
               TestingUtil.sleepThread(10);

            // simulate slow back end store
            store.lock.lock();
            try {
               cache.remove("k1");
               TestingUtil.sleepThread(100); // wait until the first AsyncProcessor thread is blocked
               cache.remove("k1"); // make second AsyncProcessor thread burn asyncProcessorIds
               TestingUtil.sleepThread(200); // wait for reaper to collect InternalNullEntry

               assert null == cache.get("k1") : "cache must return k1 == null (was: " + cache.get("k1") + ")";
            } finally {
               store.lock.unlock();
            }
         }
      });
   }

   public void testEndToEndNPE() throws Exception {
      TestingUtil.withCacheManager(new OneEntryCacheManagerCallable(false, 1) {
         @Override
         public void call() {
            cache.put("k1", "v1");
            cache.remove("k1");
            // this causes NPE in AsyncStore.isLocked(InternalNullEntry.getKey())
            cache.put("k2", "v2");
         }
      });
   }
}
