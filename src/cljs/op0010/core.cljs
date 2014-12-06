(ns op0010.core
  (:require-macros [cljs.core.async.macros :refer [go go-loop alt!]])
  (:require [cljs.core.async :as async :refer [>! <!]]))


;;;; 方針:
;;;; - これはClojure Advent Calendarのコードサンプルも兼ねているので、
;;;;   シンプルさを優先する必要がある。
;;;;   - 一つのソースファイルで実装する
;;;;   - 極力、外部モジュールの利用をなくす
;;;;   - 将来に音やセーブ機能をつけたくなったら、ブランチを分けてから拡張する
;;;; - chanを返す関数の名前の末尾には $ をつける
;;;;   - 型推論のある言語から失笑されそうだが、とりあえずシンプルさ優先で
;;;; - sweet-alert.js は、index.html側で既にロードされているという方針
;;;;   - swal側がそのままではgoog.requireできる形式になっていない為、
;;;;     手を加えないと :foreign-libs での読み込みはできない


(def ^:private article-url "https://github.com/ayamada/op0010/blob/master/clojure-advent-calendar-2014.md")





;;;
;;; image
;;;

(def ^:private current-img (atom nil))
(defn get-img [] @current-img)
(defn set-img! [filename] (reset! current-img (str "assets/img/" filename)))
(defn hide-img! [] (reset! current-img nil))
(defn call-with-img [filename body-fn]
  ;; NB: 今回は「1ファイル」という制約があるので、マクロが使えない…
  (let [old-img (get-img)
        _ (set-img! filename)
        r (body-fn)
        _ (set-img! old-img)]
    r))




;;;
;;; status
;;;

(def ^:private current-title (atom ""))
(defn get-title [] @current-title)
(defn set-title! [t] (reset! current-title t))
(defn call-with-title [filename body-fn]
  ;; NB: 今回は「1ファイル」という制約があるので、マクロが使えない…
  (let [old-title (get-title)
        _ (set-title! filename)
        r (body-fn)
        _ (set-title! old-title)]
    r))
;;; TODO: titleはステータス表示欄として利用する予定なので、それ用のユーティリティ関数をもっと用意する





;;;
;;; swal
;;;

(defn- make-swal-params [m]
  ;; NB: 注釈を書いてないパラメータはmで上書きする想定
  (clj->js
    (merge {:title (get-title)
            :text nil
            :type nil ; 今回は組み込みアイコンは全く使わない
            :allowOutsideClick false ; 今回はダイアログを閉じる事はない
            :showCancelButton false
            :confirmButtonText "OK"
            :cancelButtonText "Cancel"
            :confirmButtonColor "#3e5ef4" ; 色指定
            :closeOnConfirm false ; 今回はダイアログを閉じる事はない
            :closeOnCancel false ; 今回はダイアログを閉じる事はない
            :imageUrl (get-img)
            :imageSize nil ; 画像サイズは原寸表示とする
            :timer nil
            }
           m)))

(defn swal$ [m]
  (let [c (async/chan)]
    (js/swal (make-swal-params m) #(async/put! c (not (not %))))
    c))

(defn msg$ "メッセージ表示" [msg & [label-next]]
  (swal$ {:text msg
          :confirmButtonText (or label-next "次")}))

(defn choose$ "選択肢表示" [msg & [label-yes label-no]]
  (swal$ {:text msg
          :showCancelButton true
          :confirmButtonText (or label-yes "はい")
          :cancelButtonText (or label-no "いいえ")}))


;;;
;;; fns
;;;

(defn- init! "ゲーム全体を初期化" []
  (hide-img!)
  (set-title! "")
  nil)

(defn- boot-msg$ "起動画面" []
  (call-with-title
    "はじめに"
    ;; NB: urlをリンク化したいが、sweetAlertではできないようだ
    #(msg$ (str "これは Clojure Advent Calendar 2014 九日目の記事"
                "である『core.asyncと押し入れクエスト』に付属する"
                "ブラウザゲーム『押し入れクエスト』です。\n"
                "\n"
                "該当記事の本体は " article-url " にあります。\n"
                "\n")
           "『押し入れクエスト』を開始する")))

(defn- opening-demo$ "オープニングデモ" []
  (go
    ;; TODO: 絵をつける
    (<! (msg$ "TODO: あとでちゃんとしたOPつくります…" "プレイ開始"))
    ;(<! (msg$ ""))
    ;(<! (msg$ ""))
    ;(<! (msg$ ""))
    ;(<! (msg$ ""))
    ;; TODO
    true))

(defn- ending$ []
  (go
    (hide-img!)
    (set-title! "おしまい")
    (<! (swal$ {:text "おわってしまった"
                :confirmButtonText "完"
                :closeOnConfirm true}))))

;;;
;;; main
;;;

(defn ^:export main []
  (go
    (init!)
    (<! (boot-msg$))
    (when (<! (choose$ "オープニングデモを見ますか？"))
      (<! (opening-demo$)))
    ;; TODO
    (<! (msg$ "hi"))
    (<! (msg$ "there"))
    (<! (msg$ "test2"))
    (<! (msg$ "test3"))
    (<! (ending$))))



