(ns datalevin.remote-test
  (:require [datalevin.remote :as sut]
            [datalevin.server :as srv]
            [datalevin.storage :as st]
            [datalevin.lmdb :as l]
            [datalevin.datom :as d]
            [datalevin.constants :as c]
            [datalevin.util :as u]
            [clojure.test :refer [is are testing deftest use-fixtures]])
  (:import [java.util UUID]
           [datalevin.remote DatalogStore]
           [datalevin.datom Datom]))

(defn server-fixture
  [f]
  (let [server (srv/create {:port c/default-port
                            :root (u/tmp-dir
                                    (str "remote-test-" (UUID/randomUUID)))})]
    (try
      (srv/start server)
      (f)
      (catch Exception e (throw e))
      (finally (srv/stop server)))))

(use-fixtures :each server-fixture)

(deftest dt-store-ops-test
  (let [dt-dir "dtlv://datalevin:datalevin@localhost/test"
        store  (sut/open dt-dir)]
    (is (instance? datalevin.remote.DatalogStore store))
    (is (= c/implicit-schema (st/schema store)))
    (is (= c/e0 (st/init-max-eid store)))
    (let [a  :a/b
          v  (UUID/randomUUID)
          d  (d/datom c/e0 a v)
          s  (assoc (st/schema store) a {:db/aid 1})
          b  :b/c
          p1 {:db/valueType :db.type/uuid}
          v1 (UUID/randomUUID)
          d1 (d/datom c/e0 b v1)
          s1 (assoc s b (merge p1 {:db/aid 2}))
          c  :c/d
          p2 {:db/valueType :db.type/ref}
          v2 (long (rand c/emax))
          d2 (d/datom c/e0 c v2)
          s2 (assoc s1 c (merge p2 {:db/aid 3}))
          t1 (st/last-modified store)]
      (st/load-datoms store [d])
      (is (< t1 (st/last-modified store)))
      (is (= s (st/schema store)))
      (is (= 1 (st/datom-count store :eav)))
      (is (= 1 (st/datom-count store :ave)))
      (is (= 0 (st/datom-count store :vea)))
      (is (= [d] (st/fetch store d)))
      (is (= [d] (st/slice store :eavt d d)))
      (is (= true (st/populated? store :eav d d)))
      (is (= 1 (st/size store :eav d d)))
      (is (= d (st/head store :eav d d)))
      (st/swap-attr store b merge p1)
      (st/load-datoms store [d1])
      (is (= s1 (st/schema store)))
      (is (= 2 (st/datom-count store :eav)))
      (is (= 2 (st/datom-count store :ave)))
      (is (= 0 (st/datom-count store :vea)))
      (is (= [] (st/slice store :eav d (d/datom c/e0 :non-exist v1))))
      (is (= 0 (st/size store :eav d (d/datom c/e0 :non-exist v1))))
      (is (nil? (st/populated? store :eav d (d/datom c/e0 :non-exist v1))))
      (is (= d (st/head store :eav d d1)))
      (is (= 2 (st/size store :eav d d1)))
      (is (= [d d1] (st/slice store :eav d d1)))
      (is (= [d d1] (st/slice store :ave d d1)))
      (is (= [d1 d] (st/rslice store :eav d1 d)))
      (is (= [d d1] (st/slice store :eav
                              (d/datom c/e0 a nil)
                              (d/datom c/e0 nil nil))))
      (is (= [d1 d] (st/rslice store :eav
                               (d/datom c/e0 b nil)
                               (d/datom c/e0 nil nil))))
      (is (= 1 (st/size-filter store :eav
                               (fn [^Datom d] (= v (.-v d)))
                               (d/datom c/e0 nil nil)
                               (d/datom c/e0 nil nil))))
      (is (= d (st/head-filter store :eav
                               (fn [^Datom d] (= v (.-v d)))
                               (d/datom c/e0 nil nil)
                               (d/datom c/e0 nil nil))))
      (is (= [d] (st/slice-filter store :eav
                                  (fn [^Datom d] (= v (.-v d)))
                                  (d/datom c/e0 nil nil)
                                  (d/datom c/e0 nil nil))))
      (is (= [d1 d] (st/rslice store :ave d1 d)))
      (is (= [d d1] (st/slice store :ave
                              (d/datom c/e0 a nil)
                              (d/datom c/e0 nil nil))))
      (is (= [d1 d] (st/rslice store :ave
                               (d/datom c/e0 b nil)
                               (d/datom c/e0 nil nil))))
      (is (= [d] (st/slice-filter store :ave
                                  (fn [^Datom d] (= v (.-v d)))
                                  (d/datom c/e0 nil nil)
                                  (d/datom c/e0 nil nil))))
      (st/swap-attr store c merge p2)
      (st/load-datoms store [d2])
      (is (= s2 (st/schema store)))
      (is (= 3 (st/datom-count store c/eav)))
      (is (= 3 (st/datom-count store c/ave)))
      (is (= 1 (st/datom-count store c/vea)))
      (is (= [d2] (st/slice store :vea
                            (d/datom c/e0 nil v2)
                            (d/datom c/emax nil v2))))
      (st/load-datoms store [(d/delete d)])
      (is (= 2 (st/datom-count store c/eav)))
      (is (= 2 (st/datom-count store c/ave)))
      (is (= 1 (st/datom-count store c/vea)))
      (st/close store)
      (is (st/closed? store))
      (let [store (sut/open dt-dir)]
        (is (= [d1] (st/slice store :eav d1 d1)))
        (st/load-datoms store [(d/delete d1)])
        (is (= 1 (st/datom-count store c/eav)))
        (st/load-datoms store [d d1])
        (is (= 3 (st/datom-count store c/eav)))
        (st/close store))
      (let [d     :d/e
            p3    {:db/valueType :db.type/long}
            s3    (assoc s2 d (merge p3 {:db/aid 4}))
            s4    (assoc s3 :f/g {:db/aid 5 :db/valueType :db.type/string})
            store (sut/open dt-dir {d p3})]
        (is (= s3 (st/schema store)))
        (st/set-schema store {:f/g {:db/valueType :db.type/string}})
        (is (= s4 (st/schema store)))))
    (st/close store)))

(deftest kv-store-ops-test
  (let [dir  (str "dtlv://datalevin:datalevin@localhost/testkv?store="
                  c/db-store-kv)
        lmdb (sut/open-kv dir)]
    (l/open-dbi lmdb "a")
    (l/open-dbi lmdb "b")
    (l/open-dbi lmdb "c" (inc Long/BYTES) (inc Long/BYTES))
    (l/open-dbi lmdb "d")

    (testing "list dbis"
      (is (= #{"a" "b" "c" "d"} (set (l/list-dbis lmdb)))))

    (testing "transact-kv"
      (l/transact-kv lmdb
                     [[:put "a" 1 2]
                      [:put "a" 'a 1]
                      [:put "a" 5 {}]
                      [:put "a" :annunaki/enki true :attr :data]
                      [:put "a" :datalevin ["hello" "world"]]
                      [:put "a" 42 (d/datom 1 :a/b {:id 4}) :long :datom]
                      [:put "b" 2 3]
                      [:put "b" (byte 0x01) #{1 2} :byte :data]
                      [:put "b" (byte-array [0x41 0x42]) :bk :bytes :data]
                      [:put "b" [-1 -235254457N] 5]
                      [:put "b" :a 4]
                      [:put "b" :bv (byte-array [0x41 0x42 0x43]) :data :bytes]
                      [:put "b" 1 :long :long :data]
                      [:put "b" :long 1 :data :long]
                      [:put "b" 2 3 :long :long]
                      [:put "b" "ok" 42 :string :int]
                      [:put "d" 3.14 :pi :double :keyword]]))

    (testing "entries"
      (is (= 4 (:entries (l/stat lmdb))))
      (is (= 6 (:entries (l/stat lmdb "a"))))
      (is (= 6 (l/entries lmdb "a")))
      (is (= 10 (l/entries lmdb "b"))))

    (testing "get-value"
      (is (= 2 (l/get-value lmdb "a" 1)))
      (is (= [1 2] (l/get-value lmdb "a" 1 :data :data false)))
      (is (= true (l/get-value lmdb "a" :annunaki/enki :attr :data)))
      (is (= (d/datom 1 :a/b {:id 4}) (l/get-value lmdb "a" 42 :long :datom)))
      (is (nil? (l/get-value lmdb "a" 2)))
      (is (nil? (l/get-value lmdb "b" 1)))
      (is (= 5 (l/get-value lmdb "b" [-1 -235254457N])))
      (is (= 1 (l/get-value lmdb "a" 'a)))
      (is (= {} (l/get-value lmdb "a" 5)))
      (is (= ["hello" "world"] (l/get-value lmdb "a" :datalevin)))
      (is (= 3 (l/get-value lmdb "b" 2)))
      (is (= 4 (l/get-value lmdb "b" :a)))
      (is (= #{1 2} (l/get-value lmdb "b" (byte 0x01) :byte)))
      (is (= :bk (l/get-value lmdb "b" (byte-array [0x41 0x42]) :bytes)))
      (is (Arrays/equals ^bytes (byte-array [0x41 0x42 0x43])
                         ^bytes (l/get-value lmdb "b" :bv :data :bytes)))
      (is (= :long (l/get-value lmdb "b" 1 :long :data)))
      (is (= 1 (l/get-value lmdb "b" :long :data :long)))
      (is (= 3 (l/get-value lmdb "b" 2 :long :long)))
      (is (= 42 (l/get-value lmdb "b" "ok" :string :int)))
      (is (= :pi (l/get-value lmdb "d" 3.14 :double :keyword))))

    (testing "delete"
      (l/transact-kv lmdb [[:del "a" 1]
                           [:del "a" :non-exist]])
      (is (nil? (l/get-value lmdb "a" 1))))

    (testing "entries-again"
      (is (= 5 (l/entries lmdb "a")))
      (is (= 10 (l/entries lmdb "b"))))

    (testing "non-existent dbi"
      (is (thrown? Exception (l/get-value lmdb "z" 1))))

    (testing "handle val overflow automatically"
      (l/transact-kv lmdb [[:put "c" 1 (range 100000)]])
      (is (= (range 100000) (l/get-value lmdb "c" 1))))

    (testing "key overflow throws"
      (is (thrown? Exception (l/transact-kv lmdb [[:put "a" (range 1000) 1]]))))

    (testing "close then re-open, clear and drop"
      (let [dir (l/dir lmdb)]
        (l/close-kv lmdb)
        (is (l/closed-kv? lmdb))
        (let [lmdb  (l/open-kv dir)
              dbi-a (l/open-dbi lmdb "a")]
          (is (= "a" (l/dbi-name dbi-a)))
          (is (= ["hello" "world"] (l/get-value lmdb "a" :datalevin)))
          (l/clear-dbi lmdb "a")
          (is (nil? (l/get-value lmdb "a" :datalevin)))
          (l/drop-dbi lmdb "a")
          (is (thrown? Exception (l/get-value lmdb "a" 1)))
          (l/close-kv lmdb))))
    (u/delete-files dir)))
