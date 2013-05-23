;; Copyright Dilip Chhetri (2013)

;; Base directory used to store the contents
(def stor-bkup-dir "/mnt/scratch/tmp/stor")

;; write data-structure 'ds' to filename, which can be later read-back
;; using deserialize
(defn f-serialize [ds #^String filename]
  (with-open [wr (clojure.java.io/writer filename)]
    (binding [*print-dup* true *out* wr]
      (prn ds))))
 
 
;; This allows us to then read in the structure at a later time
(defn f-deserialize [filename]
  (with-open [r (java.io.PushbackReader. (java.io.FileReader. filename))]
    (read r)))

;; String based deserializer
(defn s-deserialize [#^String s]
  (let [r (java.io.PushbackReader. (java.io.StringReader. s))]
    (read r)))

;; size of chunk with which file will be chopped and checksummed
(def csum-chunk-size 32768)
;;(def csum-chunk-size 1024)

;; finalize the digest by padding and return string representation
(defn digestDone [digest buf len]
  (apply str (map (partial format "%02x") (.digest digest))))

;; organize md5 o/p such that it is ordered as,
;;   [0 file-len md5] [0 chunk1 md5] [chunk1 chunk2 md5] ...
(defn md5-organized-fmt [inp]
  (cons (first inp) (reverse (rest inp))))

;; Parameter used to compute rolling-hash
;; We can chose power-of-two numbers as well for faster compute
(def rolling-hash-base 257)
(def rolling-hash-mod 2147483648)
(defn rolling-hash [s len]
  ;; It is pretty slow to do such computation in clojure, therefore setting
  ;; 'off' to 'len' instead of 0, it will always return 0 for now
  (loop [h 0 off len]
    (if (< off len)
      (recur (mod (+ (* rolling-hash-base (aget s off)) h) rolling-hash-mod) (inc off))
      h)))

(defn md5file [file]
  (with-open [input (java.io.FileInputStream. file)]
    (let [d1 (java.security.MessageDigest/getInstance "MD5")
          bufsize 262144
          buf (byte-array bufsize)
         ]
      (loop [last_cnt 0]
        (let [cnt (.read input buf 0 bufsize)]
          (if (= -1 cnt)
            (digestDone d1 buf last_cnt)
            (do
              (.update d1 buf 0 cnt)
              (recur cnt))))))))

(defn md5entry [off cnt md5 rhash]
  {:off off :count cnt :md5 md5 :rhash rhash})

(defn md5set [file]
  (with-open [input (java.io.FileInputStream. file)]
    (let [d1 (java.security.MessageDigest/getInstance "MD5")
          d2 (java.security.MessageDigest/getInstance "MD5")
          bufsize csum-chunk-size
          buf (byte-array bufsize)
         ]
      (loop [off 0 last_cnt 0 ent ()]
        (let [cnt (.read input buf 0 bufsize)]
          (if (= -1 cnt)
            (let [md5 (digestDone d1 buf last_cnt)
                  rhash (rolling-hash buf cnt)]
              (md5-organized-fmt (cons (md5entry 0 off md5 rhash) ent)))
            (do
              (.reset d2)
              (.update d1 buf 0 cnt)
              (.update d2 buf 0 cnt)
              (let [md5 (digestDone d2 buf cnt)
                    rhash (rolling-hash buf cnt)]
                (recur (+ off cnt) cnt (cons (md5entry off cnt md5 rhash) ent))))))))))

(defn now-seconds []
  (int (/ (System/currentTimeMillis) 1000)))

;; return seq of File
(defn walk-dir [dir]
  (remove #(.isDirectory %)
    (file-seq (java.io.File. dir))))
;; return seq of String:file-name
(defn walk-dir2 [dir]
  (map 
    #(.getCanonicalPath %)
    (filter #(.isFile %) (file-seq (java.io.File. dir)))))

;; list of index-db's
(defn index-db-list [idxdir]
  (filter  #(re-matches #"file_index.*" %)
    (map #(.getName %)
     (file-seq
       (java.io.File.
         (str idxdir "/index"))))))

;; greatest index-db with time-stamp >= "ts"
(defn prev-index-db [idxdir ts]
  (let [prev-ts
    (first
      (sort #(> %1 %2)
        (filter #(<= % ts)
          (map
            #(Integer/parseInt %)
            (map
              #(clojure.string/replace % #"file_index." "")
              (index-db-list idxdir))))))]
    (if prev-ts
      (str idxdir "/index/file_index." prev-ts)
      nil)))

(defn mkdir [dir]
  (let [d (java.io.File. dir)]
    (cond
      (.isDirectory d) true
      :else (.mkdirs d))))

(defn setup-bkdir [dir]
  (let [d (java.io.File. dir)]
    (if-not (.isDirectory d)
      (do 
        (mkdir dir)
        (mkdir (str dir "/.metadata/index")))
      true)))

;; strips the last components after "/"
(defn dirname [^String path]
  (let [comps (.split #"/" path)
        cnt (count comps)]
    (apply str (interpose "/" (take (dec cnt) comps)))))

;; get normalized path
(defn normalized-path [path]
  (.getCanonicalPath (java.io.File. path)))

(defn open-idx-writer [idx]
  (clojure.java.io/writer idx :append true))

(defn close-idx [idx]
  (.close idx))

(defn idx-tbl-add [idx file dst info md5]
  (.write idx (str file "|" dst "|" info "|" md5 "\n")))

;; copy one file, making directory if needed
(defn copy-one-file [verbose src dst]
  (let [parent (.getParent (java.io.File. dst))]
    (mkdir parent)
    (if verbose (println (str "Copy: " src)))
    (clojure.java.io/copy (clojure.java.io/file src) (clojure.java.io/file dst))))

;; parse file into hash-map entries of the form,
;;  key: source file name
;;  value: (vector dst attr list-of<md5set>)
;;        where,
;;        dst: destination path on backup
;;        attr: {... posix attribute}
;;        md5set: {:off <offset> :count <blk size> :md5 <block md5> :rhash <rolling hash>})
(defn parse-idx-tbl [file]
  (with-open [r (clojure.java.io/reader file)]
    (loop [l (line-seq r)
           h (hash-map)]
      (let [s (first l)]
        (if-not s
          h
          (let [v (.split #"\|" s)
                x (s-deserialize (aget v 3))]
            (recur (rest l) (assoc h (aget v 0) x))))))))

;; =================== File Delta Generation ============================
;; convert list of md5 to hash-map
;;  md5 => [off count]
(defn md5set-tbl [md5set]
  ;; first entry is md5 for full file, ignore it
  (loop [hm (hash-map) ms (rest md5set)]
    (let [he (first ms)]
      (if-not he
        hm
        (if (get hm (:md5 he))
          (recur hm (rest ms))
          (recur (assoc hm (:md5 he) (vector (:off he) (:count he))) (rest ms)))))))

;; Generate delta to be transferred from src to dst to make 'dst' identical
;; to 'src'
;; Delta is list of entries with following format
;;  [<bool:match> <src file offset> <dst file offset> <byte count>} 
(defn gen-file-delta [src-md5set dst-md5set]
  (let [dmap (md5set-tbl dst-md5set)]
    ;; first entry is md5 for full file, ignore it
    (loop [delta-list () s-list (rest src-md5set)]
      (let [se (first s-list)
            de (if se (get dmap (:md5 se)) nil)]
        (if-not se
          delta-list 
          (if (and de (= (:count se) (get de 1)))
            (recur (cons (vector true (:off se) (get de 0) (:count se)) delta-list) (rest s-list))
            (recur (cons (vector false (:off se) 0 (:count se)) delta-list) (rest s-list))))))))
;; =================== File Delta Generation ============================

;; =================== File Attributes ==================================
(def no-follow-links
  (into-array [java.nio.file.LinkOption/NOFOLLOW_LINKS]))

(def java-posix-fattr
  java.nio.file.attribute.PosixFileAttributes)

(defn posix-fattr-permissions [posix-fattr]
  (java.nio.file.attribute.PosixFilePermissions/toString (.permissions posix-fattr)))

(defn posix-fattr-owner [posix-fattr]
  (.toString (.owner posix-fattr)))

(defn posix-fattr-group [posix-fattr]
  (.toString (.group posix-fattr)))

(defn posix-fattr-mtime [posix-fattr]
  (.toMillis (.lastModifiedTime posix-fattr)))

(defn posix-fattr-ctime [posix-fattr]
  (.toMillis (.creationTime posix-fattr)))

(defn posix-fattr-atime [posix-fattr]
  (.toMillis (.lastAccessTime posix-fattr)))

(defn posix-fattr-size [posix-fattr]
  (.size posix-fattr))

(defn posix-fattr-type [path posix-fattr]
  (cond (.isRegularFile posix-fattr) "f"
        (.isDirectory posix-fattr) "d"
        (.isSymbolicLink posix-fattr) (str "l:" (.toString (java.nio.file.Files/readSymbolicLink path)))
        :else "o"))

(defn get-posix-fattr [file-name]
  (java.nio.file.Files/readAttributes (.toPath (java.io.File. file-name)) java-posix-fattr no-follow-links))

(defn get-posix-finfo [file-name]
  (let [a (get-posix-fattr file-name)]
    {:type (posix-fattr-type (.toPath (java.io.File. file-name)) a)
     :size (posix-fattr-size a)
     :owner (posix-fattr-owner a)
     :group (posix-fattr-group a)
     :perm (posix-fattr-permissions a)
     :atime (posix-fattr-atime a) 
     :ctime (posix-fattr-ctime a) 
     :mtime (posix-fattr-mtime a)}))

(defn to-posix-fattr-permissions [s]
  (java.nio.file.attribute.PosixFilePermissions/fromString s))

(defn set-posix-fattr-permissions [path #^String perm]
  (java.nio.file.Files/setPosixFilePermissions path (to-posix-fattr-permissions perm)))

(defn set-posix-fattr-mtime [path #^Long milli-sec]
  (java.nio.file.Files/setLastModifiedTime path (java.nio.file.attribute.FileTime/fromMillis milli-sec)))

(defn set-posix-fattr [file-name attr]
  (let [path (.toPath (java.io.File. file-name))]
    (set-posix-fattr-permissions path (:perm attr))
    (set-posix-fattr-mtime path (:mtime attr))))

;; =================== File Attributes ==================================

;; Get max "last-modified-time" from index-file
(defn idx-max-mtime [idx]
  (apply max (map #(:mtime (s-deserialize (aget (.split #"\|" %1) 2))) (line-seq (clojure.java.io/reader idx)))))
  
(defn copy-all-files [locdir bkdir ts]
  (let [data_dir (normalized-path (str bkdir "/data/" ts))
        idx_db (normalized-path (str bkdir "/.metadata/index/file_index." ts))]
    (.createNewFile (java.io.File. idx_db))
    (with-open [idxwr (open-idx-writer idx_db)]
      (doseq [ff (map #(.getCanonicalPath %) (walk-dir locdir))]
        (let [dst (str data_dir "/" ff)]
          (copy-one-file false ff dst)
          (set-posix-fattr-permissions (.toPath (java.io.File. dst)) "r--------")
          (idx-tbl-add idxwr ff dst (get-posix-finfo ff) (md5set ff)))))))

;; Further optimization possibilities,
;; - compare last-modified-time, and if they are same, no need to compare md5
(defn copy-modified-files [locdir bkdir ts old-idx-db]
  (let [data_dir (normalized-path (str bkdir "/data/" ts))
        idx_db (normalized-path (str bkdir "/.metadata/index/file_index." ts))
        itbl (parse-idx-tbl old-idx-db)]
    (.createNewFile (java.io.File. idx_db))
    (with-open [new-idx (open-idx-writer idx_db)]
      (doseq [ff (map #(.getCanonicalPath %) (walk-dir locdir))]
        (let [bkmd5 (:md5 (first (get itbl ff {:md5 "not-found"})))
              locmd5set (md5set ff)
              locmd5 (:md5 (first locmd5set))
              dst (str data_dir "/" ff)]
          ;; XXX: when file are same the "dst" hs to be copied over from the previous idx table
          (idx-tbl-add new-idx ff dst (get-posix-finfo ff) locmd5set)
          (if (not= locmd5 bkmd5)
            (do
              (copy-one-file true ff dst)
              (set-posix-fattr-permissions (.toPath (java.io.File. dst)) "r--------"))))))))

(defn backup-files [locdir bkdir]
  (setup-bkdir bkdir)
  (let [cur-ts (now-seconds)
        idx-db (prev-index-db (str bkdir "/.metadata") cur-ts)]
    (if idx-db
      (copy-modified-files locdir bkdir cur-ts idx-db)
      (copy-all-files locdir bkdir cur-ts))))

(defn restore-files
  ([locdir bkdir] (restore-files locdir bkdir (now-seconds)))

  ([locdir bkdir ts]
    (let [idx-db (prev-index-db (str bkdir "/.metadata") ts)]
      (if-not idx-db
        (println (str "No backup found prior to time-stamp " ts))
        (with-open [r (clojure.java.io/reader idx-db)]
          (doseq [l (line-seq r)]
            (let [v (.split #"\|" l)
                  to (str locdir "/" (aget v 0))
                  from (aget v 1)
                  attr (s-deserialize (aget v 2))]
              (copy-one-file true from to)
              (set-posix-fattr to attr))))))))

;; Setup directory to contain new-transaction
;; returns transaction-id
(defn open-new-transaction [stor]
  (let [txn-id (str "txn-" (now-seconds))
        bkdir (str stor-bkup-dir "/" stor "/" txn-id)
        cur-ts (now-seconds)
        prev-db (prev-index-db (str bkdir "/.metadata") cur-ts)
        idx-db (str bkdir "/.metadata/index/file_index." cur-ts)]
    (mkdir (str bkdir "/data"))
    (mkdir (str bkdir "/.metadata/index"))
    (if prev-db (copy-one-file prev-db idx-db) (.createNewFile (java.io.File. idx-db)))
    txn-id))

;; ============ HTTP service ==========================

(def rest-service-port 8080)
(def api-top "/pumkin/v1")

(use 'compojure.core)
(use 'ring.adapter.jetty)
(require '[compojure.route :as route])

;; POST /pumkin/v1/<stor name>/sync-txn
(defn start-txn-handler [stor]
  (let [txn-id (open-new-transaction stor)
        location (str api-top "/" stor "/" txn-id)
        col-link (str "<link rel=\"collection\" type=\"text/plain\" href=\"" location "\">")
        toc-link (str "<link rel=\"index\" type=\"text/plain\" href=\"" location "/index\">")]
    {:status 201
     :headers {"Location:" location
               "Content-Type:" "text/html"}
     :body (str "<head>\n"
                col-link "\n"
                toc-link "\n"
                "</head>")
    }))

;; DELETE /pumkin/v1/<stor name>/<transaction-id>
(defn abort-txn-handler [req]
  {:status 200
   :headers {}
   :body ""
  })

;; POST /pumkin/v1/<stor name>/<transaction-id>
(defn commit-txn-handler [req]
  {:status 202
   :headers {}
   :body ""
  })

;; PUT /pumkin/v1/<stor name>/<transaction-id>/<file-path>
;; POST /pumkin/v1/<stor name>/<transaction-id>/<file-path>
;; X-pumkin-md5set: 
;; X-pumkin-attr: 
(defn txn-add-handler [stor txn file req]
  (let [ts (aget (.split #"-" txn) 1)
        txn-dir (format "%s/%s/%s" stor-bkup-dir stor txn)
        idx (format "%s/.metadata/index/file_index.%s" txn-dir ts)
        dst (format "%s/data/%s/%s" txn-dir ts file)
        src (req :body)]
    (mkdir (dirname dst))
    (clojure.java.io/copy src (clojure.java.io/file dst) :buffer-size 4096)
    (with-open [idxwr (open-idx-writer idx)]
      (idx-tbl-add idxwr file dst (get-posix-finfo dst) (md5set dst)))
    {:status 202
     :headers {}
     :body ""
    }))

;; GET /pumkin/v1/<stor name>/<transaction-id>/index
(defn txn-info-pfile-idx-handler [stor txn-id]
  (let [ts (aget (.split #"-" txn-id) 1)
        idx (str stor-bkup-dir "/" stor "/" txn-id "/.metadata/index/file_index." ts)]
    (ring.util.response/file-response idx)
  ))

(defroutes rest-routes
  (GET "/" [] "<p> Pumkin Home </p>")
  (POST "/pumkin/v1/:stor/sync-txn" [stor] (start-txn-handler stor))
  (DELETE "/pumkin/v1/:stor/:txn-id" [stor txn-id] (abort-txn-handler stor txn-id))
  (POST "/pumkin/v1/:stor/:txn-id" [stor txn-id] (commit-txn-handler stor txn-id))
  (POST ["/pumkin/v1/:stor/:txn-id/data/:file" :file #".*"] [stor txn-id file :as req] (txn-add-handler stor txn-id file req))
  (GET "/pumkin/v1/:stor/:txn-id/index" [stor txn-id] (txn-info-pfile-idx-handler stor txn-id))
  (ANY "*" [] "<p>Page not found. </p>"))

(defn start-http-server []
  (run-jetty rest-routes {:port rest-service-port
                          :join? true}))
