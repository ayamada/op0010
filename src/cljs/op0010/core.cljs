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



;;; TODO: 開始直後はチュートリアル的なものを入れたい(フラグで判定する)



(def ^:private article-url "https://github.com/ayamada/op0010/blob/master/clojure-advent-calendar-2014.md")




;;;
;;; game data
;;;


;;; これをシリアライズしてwrite/readすれば、セーブとロードが実現できる
(def ^:private the-game-data (atom {}))
(defn- init-game-data! []
  (reset! the-game-data {:flags #{}
                         :inventory []
                         :player {:hp 10
                                  :hp-max 10
                                  :atk 5
                                  :exp 0
                                  :lv 1
                                  }
                         :floor 1
                         :step 0
                         }))
(defn- get-data [k & [fallback]] (get @the-game-data k fallback))
(defn- flag [k] (get-in @the-game-data [:flags k]))
(defn- flag-on! [k]
  (swap! the-game-data #(assoc % :flags (conj (:flags %) k))))
(defn- flag-off! [k]
  (swap! the-game-data #(assoc % :flags (disj (:flags %) k))))
(defn- has-inventory? [k]
  ;; TODO
  nil)
(defn- add-inventory! [k]
  ;; TODO
  nil)
(defn- consume-inventory! [k]
  ;; TODO
  nil)
(defn- get-player-data [k & [fallback]]
  (get-in @the-game-data [:player k] fallback))
(defn- set-player-data! [k v]
  (swap! the-game-data assoc-in [:player k] v))




;;;
;;; image
;;;

(def ^:private current-img (atom nil))
(defn get-img [] @current-img)
(defn set-img! [filename]
  (reset! current-img (and filename (str "assets/img/" filename))))
(defn hide-img! [] (set-img! nil))





;;;
;;; title(display status)
;;;

(def ^:private current-title (atom ""))
(defn get-title [] @current-title)
(defn set-title! [title] (reset! current-title title))
(defn call-with-title [title body-fn]
  ;; NB: 今回は「1ファイル」という制約があるので、マクロが使えない…
  (let [old-title (get-title)
        _ (set-title! title)
        r (body-fn)
        _ (set-title! old-title)]
    r))


(defn- update-title-status! []
  (let [hp (get-player-data :hp)
        hp-max (get-player-data :hp-max)
        new-label (str
                    "HP: " hp "/" hp-max " "
                    ;; TODO
                    ""
                    )]
    (set-title! new-label)))




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
            :imageSize nil ; これは上手く動作しないようだ
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
          :confirmButtonText (or label-yes " はい ")
          :cancelButtonText (or label-no "いいえ")}))


;;;
;;; game events
;;;

;;; 「ゲームイベント」はとりあえずここに登録する。keyはkeyword、valはfn。
(def ^:private all-game-events (atom {}))

;;; フロア毎にゲームイベントテーブルは違う。keyはフロア値、valはテーブル本体。
(def ^:private the-game-event-tables (atom {}))

;;; NB: 本当は関数ではなくマクロにしたい
(defn- register-game-event! [k f]
  (swap! all-game-events assoc k f))
(defn- register-game-event-table! [floor static dynamic]
  (swap! the-game-event-tables assoc floor {:static static, :dynamic dynamic}))

(defn- invoke-event$ [k]
  (if-let [f (get @all-game-events k)]
    (f)
    (msg$ (str "エラー: " k " に対応するイベントは定義されていません"))))

(register-game-event!
  :normal-1-b1f$
  #(msg$ "探検している"))
(register-game-event!
  :normal-2-b1f$
  #(msg$ "ここは薄暗い"))
(register-game-event!
  :normal-3-b1f$
  #(msg$ "しめっている"))

(register-game-event-table!
  ;; フロア番号
  1
  ;; ステップ数固定のイベント(あれば)
  {}
  ;; ランダムイベントのテーブル(rand-nthで選択される)
  [:normal-1-b1f$ :normal-2-b1f$ :normal-3-b1f$])





;;; NB: この関数自体はgoブロックを持たないが、返り値としてchanを返す
(defn- invoke-step-event$ "現在位置のイベントを実行" []
  (let [current-floor (get-data :floor)
        current-step (get-data :step)
        event-table (get @the-game-event-tables current-floor)]
    ;; TODO: あとで、fallbackなフロアを用意する等、とにかくエラーにはならないようにしたい
    (when-not event-table
      (js/alert "エラー: フロアに対応するデータがありません"))
    (if-let [static-event-key (get (:static event-table) current-step)]
      (invoke-event$ static-event-key)
      (if-let [dynamic-event-key (rand-nth (:dynamic event-table))]
        (invoke-event$ dynamic-event-key)
        ;; TODO: ここも上記同様、あとでとにかくエラーにならないようにする
        (msg$ "エラー: イベントが空です")))))



;;;
;;; fns
;;;

(defn- init! "ゲーム全体を初期化" []
  (hide-img!)
  (set-title! "")
  (init-game-data!))

(defn- boot-msg$ "起動画面" []
  (call-with-title
    "はじめに"
    ;; NB: urlをリンク化したいが、sweetAlertではできないようだ
    #(msg$ (str "これは Clojure Advent Calendar 2014 九日目の記事"
                "である『core.asyncとおしいれクエスト』に付属する"
                "ブラウザゲーム『おしいれクエスト』です。\n"
                "\n"
                "該当記事の本体は " article-url " にあります。\n"
                "\n")
           "『おしいれクエスト』を開始する")))

(defn- opening-demo$ "オープニングデモ" []
  (go
    (set-img! "door2.jpg")
    (<! (msg$ "ぼくの家には、不思議なおしいれがある。"))
    (set-img! "closet1.png")
    (<! (msg$ "今日は、○○を探しにおしいれに入った。"))
    (set-img! "black.png")
    (<! (msg$ "そしたら、急におしいれがしまり、開かなくなってしまった。"))
    (<! (msg$ "おしいれの奥の方はどこまでも続いている。"))
    (hide-img!)
    (<! (msg$ "こうして、ぼくの冒険がはじまった！" "プレイ開始"))
    true))

(defn- ending$ []
  (go
    (set-img! "oshimai.png")
    (set-title! "おしまい")
    (<! (swal$ {:text "おわってしまった"
                :confirmButtonText "完"
                :closeOnConfirm true}))))

(defn- is-game-finished? []
  ;; TODO
  nil)

;;; ステータスを見て、必要であれば、各種の処理を行う
(defn- hoge-status$ []
  (go
    ;; TODO
    (cond
      ;; - HPが0なら、ゲームオーバー
      ;; TODO
      ;; - HPが減っていれば、アイテムを使って回復するかどうか
      ;; TODO
      :else nil)))


;;;
;;; main
;;;

(defn ^:export main []
  (go
    (init!)
    (<! (boot-msg$))
    (when (<! (choose$ "オープニングデモを見ますか？"))
      (<! (opening-demo$)))
    (while (not (is-game-finished?))
      ;; ステータスチェック
      (<! (hoge-status$))
      ;; ステータス更新
      (update-title-status!)
      ;; 一歩進む
      (swap! the-game-data update-in [:step] inc) ; stepが1増える
      ;; このステップでのイベントを実行
      (<! (invoke-step-event$))
      ;; 念の為の無限ループ避け用
      (<! (async/timeout 1)))
    (<! (ending$))))



