package org.infinispan.distribution;

import org.infinispan.Cache;
import org.infinispan.commands.VisitableCommand;
import org.infinispan.commands.write.PutKeyValueCommand;
import org.infinispan.config.Configuration;
import static org.infinispan.config.Configuration.CacheMode.DIST_ASYNC;
import static org.infinispan.config.Configuration.CacheMode.DIST_SYNC;
import org.infinispan.container.DataContainer;
import org.infinispan.container.entries.ImmortalCacheEntry;
import org.infinispan.container.entries.InternalCacheEntry;
import org.infinispan.container.entries.MortalCacheEntry;
import org.infinispan.remoting.transport.Address;
import org.infinispan.test.MultipleCacheManagersTest;
import org.infinispan.test.TestingUtil;
import org.infinispan.transaction.DummyTransactionManagerLookup;
import org.testng.annotations.Test;

import javax.transaction.TransactionManager;
import java.io.Serializable;
import java.util.List;

@Test(groups = "functional", testName = "distribution.BaseDistFunctionalTest")
public abstract class BaseDistFunctionalTest extends MultipleCacheManagersTest {
   protected Cache<Object, String> c1, c2, c3, c4;
   protected List<Cache<Object, String>> caches;
   protected boolean sync = true;
   protected boolean tx = false;

   protected void createCacheManagers() throws Throwable {
      Configuration c = getDefaultClusteredConfig(sync ? DIST_SYNC : DIST_ASYNC);
      if (!sync) c.setUnsafeUnreliableReturnValues(true);
      if (tx) c.setTransactionManagerLookupClass(DummyTransactionManagerLookup.class.getName());
      caches = createClusteredCaches(4, "dist", c);
      c1 = caches.get(0);
      c2 = caches.get(1);
      c3 = caches.get(2);
      c4 = caches.get(3);
   }

   // ----------------- HELPERS ----------------

   protected void initAndTest() {
      for (Cache<Object, String> c : caches) assert c.isEmpty();

      c1.put("k1", "value");
      asyncWait("k1", PutKeyValueCommand.class, getNonOwners("k1"));
      for (Cache<Object, String> c : caches)
         assert "value".equals(c.get("k1")) : "Failed on cache " + c.getCacheManager().getAddress();
      assertOwnershipAndNonOwnership("k1");
   }

   protected Cache<Object, String> getFirstNonOwner(String key) {
      return getNonOwners(key)[0];
   }

   protected Cache<Object, String> getSecondNonOwner(String key) {
      return getNonOwners(key)[1];
   }

   protected void assertOnAllCachesAndOwnership(Object key, String value) {
      for (Cache<Object, String> c : caches) {
         Object realVal = c.get(key);
         if (value == null) {
            assert realVal == null : "Expecting [" + key + "] to equal [" + value + "] on cache ["
                  + c.getCacheManager().getAddress() + "] but was [" + realVal + "]";
         } else {
            assert value.equals(realVal) : "Expecting [" + key + "] to equal [" + value + "] on cache ["
                  + c.getCacheManager().getAddress() + "] but was [" + realVal + "]";
         }
      }
      if (value != null) assertOwnershipAndNonOwnership(key);
   }

   protected void assertOwnershipAndNonOwnership(Object key) {
      for (Cache<Object, String> c : caches) {
         DataContainer dc = c.getAdvancedCache().getDataContainer();
         if (isOwner(c, key)) {
            InternalCacheEntry ice = dc.get(key);
            assert ice != null : "Fail on cache " + c.getCacheManager().getAddress() + ": dc.get(" + key + ") returned null!";
            assert ice instanceof ImmortalCacheEntry : "Fail on cache " + c.getCacheManager().getAddress() + ": dc.get(" + key + ") returned " + dc.get(key);
         } else {
            if (dc.containsKey(key)) {
               assert dc.get(key) instanceof MortalCacheEntry : "Fail on cache " + c.getCacheManager().getAddress() + ": dc.get(" + key + ") returned " + dc.get(key);
               assert dc.get(key).getLifespan() == c1.getConfiguration().getL1Lifespan();
            }
         }
      }
   }

   protected void assertIsInL1(Cache<?, ?> cache, Object key) {
      DataContainer dc = cache.getAdvancedCache().getDataContainer();
      InternalCacheEntry ice = dc.get(key);
      assert ice != null : "Entry for key [" + key + "] should be in data container on cache at [" + cache.getCacheManager().getAddress() + "]!";
      assert !(ice instanceof ImmortalCacheEntry) : "Entry for key [" + key + "] should have a lifespan on cache at [" + cache.getCacheManager().getAddress() + "]!";
   }

   protected void assertIsNotInL1(Cache<?, ?> cache, Object key) {
      DataContainer dc = cache.getAdvancedCache().getDataContainer();
      InternalCacheEntry ice = dc.get(key);
      assert ice == null : "Entry for key [" + key + "] should not be in data container on cache at [" + cache.getCacheManager().getAddress() + "]!";
   }

   protected void assertIsInContainerImmortal(Cache<?, ?> cache, Object key) {
      DataContainer dc = cache.getAdvancedCache().getDataContainer();
      InternalCacheEntry ice = dc.get(key);
      assert ice != null : "Entry for key [" + key + "] should be in data container on cache at [" + cache.getCacheManager().getAddress() + "]!";
      assert ice instanceof ImmortalCacheEntry : "Entry for key [" + key + "] on cache at [" + cache.getCacheManager().getAddress() + "] should be immortal but was [" + ice + "]!";
   }

   protected static boolean isOwner(Cache<?, ?> c, Object key) {
      DistributionManager dm = c.getAdvancedCache().getComponentRegistry().getComponent(DistributionManager.class);
      List<Address> ownerAddresses = dm.locate(key);
      for (Address a : ownerAddresses) {
         if (c.getCacheManager().getAddress().equals(a)) return true;
      }
      return false;
   }

   protected static boolean isFirstOwner(Cache<?, ?> c, Object key) {
      DistributionManager dm = c.getAdvancedCache().getComponentRegistry().getComponent(DistributionManager.class);
      List<Address> ownerAddresses = dm.locate(key);
      return c.getCacheManager().getAddress().equals(ownerAddresses.get(0));
   }

   protected Cache<Object, String>[] getOwners(Object key) {
      Cache<Object, String>[] owners = new Cache[2];
      int i = 0;
      for (Cache<Object, String> c : caches) {
         if (isOwner(c, key)) owners[i++] = c;
      }
      return owners;
   }

   protected Cache<Object, String>[] getNonOwners(Object key) {
      Cache<Object, String>[] nonOwners = new Cache[2];
      int i = 0;
      for (Cache<Object, String> c : caches) {
         if (!isOwner(c, key)) nonOwners[i++] = c;
      }
      return nonOwners;
   }

   protected List<Address> residentAddresses(Object key) {
      DistributionManager dm = c1.getAdvancedCache().getComponentRegistry().getComponent(DistributionManager.class);
      return dm.locate(key);
   }

   protected DistributionManager getDistributionManager(Cache<?, ?> c) {
      return c.getAdvancedCache().getComponentRegistry().getComponent(DistributionManager.class);
   }

   /**
    * Blocks and waits for a replication event on async caches
    *
    * @param key     key that causes the replication.  Used to determine which caches to listen on.  If null, all caches
    *                are checked
    * @param command command to listen for
    * @param caches  on which this key should be invalidated
    */
   protected void asyncWait(Object key, Class<? extends VisitableCommand> command, Cache<?, ?>... caches) {
      // no op.
   }

   protected TransactionManager getTransactionManager(Cache<?, ?> cache) {
      return TestingUtil.getTransactionManager(cache);
   }

   /**
    * A special type of key that if passed a cache in its constructor, will ensure it will always be assigned to that
    * cache (plus however many additional caches in the hash space)
    */
   protected static class MagicKey implements Serializable {
      int hashcode;
      String address;

      public MagicKey(Cache<?, ?> toMapTo) {
         address = toMapTo.getCacheManager().getAddress().toString();
         // generate a hashcode that will always map it to the specified cache.
         for (int i = 1; i < DefaultConsistentHash.HASH_SPACE; i += 100) {
            // create a dummy object with this hashcode
            final int hc = i;
            Object dummy = new Object() {
               @Override
               public int hashCode() {
                  return hc;
               }
            };

            if (BaseDistFunctionalTest.isFirstOwner(toMapTo, dummy)) {
               // we have found a hashcode that works!
               hashcode = hc;
               break;
            }
         }
      }

      @Override
      public int hashCode() {
         return hashcode;
      }

      @Override
      public boolean equals(Object o) {
         if (this == o) return true;
         if (o == null || getClass() != o.getClass()) return false;

         MagicKey magicKey = (MagicKey) o;

         if (hashcode != magicKey.hashcode) return false;
         if (address != null ? !address.equals(magicKey.address) : magicKey.address != null) return false;

         return true;
      }

      @Override
      public String toString() {
         return "MagicKey{" +
               "hashcode=" + hashcode +
               ", address='" + address + '\'' +
               '}';
      }
   }
}
