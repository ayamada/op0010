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

(def ^:private heal-threshold (/ 3 4))



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
                                  :lv 1
                                  :exp 0
                                  :exp-next 10
                                  :varied-hp? false ; HP変動フラグ
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

(defn- add-to-inventory! [k]
  (swap! the-game-data #(assoc % :inventory (conj (:inventory %) k))))

;;; 消費したらtrueを返す。持ってなければnilを返す
(defn- consume-from-inventory! [k]
  (let [inv (get-data :inventory)
        ;; 一個だけ抜く(複数持ってる場合も一個だけ抜くのでfilterは使えない)
        new-inv (loop [l inv
                       r nil]
                  (if (empty? l)
                    r
                    (let [one (first l)
                          others (rest l)]
                      (if (= k one)
                        (concat r others) ; oneを除いた全てを返す
                        (recur others (cons one r))))))]
    (when-not (= (count inv) (count new-inv))
      (swap! the-game-data assoc :inventory new-inv)
      true)))

(defn- get-player-data [k & [fallback]]
  (get-in @the-game-data [:player k] fallback))
(defn- set-player-data! [k v]
  (swap! the-game-data assoc-in [:player k] v))

;;; HPの変動は頻繁に行うので、専用関数を用意しておく
(defn- vary-hp! [v vary-flag]
  (let [hp-max (get-player-data :hp-max)
        new-hp (max 0 (min hp-max (+ (get-player-data :hp) v)))]
    (set-player-data! :varied-hp? vary-flag)
    (set-player-data! :hp new-hp)))






;;;
;;; image
;;;

(def ^:private current-img (atom nil))
(defn get-img [] @current-img)
(defn set-img! [filename]
  (reset! current-img (and filename (str "assets/img/" filename))))
(defn hide-img! [] (set-img! nil))
(defn call-with-img$ [filename body-fn$]
  (go
    ;; NB: 今回は「1ファイル」という制約があるので、マクロが使えない…
    (let [old-img (get-img)
          _ (set-img! filename)
          r (<! (body-fn$))
          _ (set-img! old-img)]
      r)))





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
  (let [floor (get-data :floor)
        step (get-data :step)
        new-label (str "地下" floor "階 階段から" step "m地点")]
    (set-title! new-label)))


;;; 上記のタイトルでは一部のステータスしか表示できない為、
;;; 残りのステータスは本文の末尾に付与する形式にする
(defn- append-current-misc-status [text]
  (let [hp (get-player-data :hp)
        hp-max (get-player-data :hp-max)
        lv (get-player-data :lv)
        exp (get-player-data :exp)
        exp-next (get-player-data :exp-next)
        ]
    (str text "\n\n"
         "----------------\n"
         "HP: " hp "/" hp-max "\n"
         "LV: " lv " EXP: " exp "/" exp-next "\n"
         ;; TODO
         ""
         )))
(def ^:private acms append-current-misc-status)



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

;;; NB: この関数自体はgoブロックを持たないが、返り値としてchanを返す
(defn- invoke-step-event$ "現在位置のイベントを実行" []
  (let [current-floor (get-data :floor)
        current-step (get-data :step)
        event-table (get @the-game-event-tables current-floor)]
    ;; TODO: あとで、fallbackなフロアを用意する等、とにかくエラーにはならないようにしたい
    (when-not event-table
      (js/alert "エラー: フロアに対応するデータがありません"))
    (call-with-img$
      "black.png"
      #(if-let [static-event-key (get (:static event-table) current-step)]
         (invoke-event$ static-event-key)
         (if-let [dynamic-event-key (rand-nth (:dynamic event-table))]
           (invoke-event$ dynamic-event-key)
           ;; TODO: ここも上記同様、あとでとにかくエラーにならないようにする
           (msg$ "エラー: イベントが空です"))))))

(defn- emit-game-over$ []
  (go
    (flag-on! :game-is-finished)
    ;; NB: ゲームオーバー画面はここではなくメインループの方で出す事に
    ;(msg$ "ゲームオーバー" "おわってしまった")
    true))


(declare item-name item-foods)

(defn- query-heal$ []
  (go
    (when-let [foods (seq (filter item-foods (get-data :inventory)))]
      (set-player-data! :varied-hp? false) ; チェックフラグを下げておく
      (when (<! (choose$ (acms (str "HPが減っている。\n"
                                    "何か食べて回復しようか？"))
                         "食べる"
                         "そのまま前進"))
        (loop [left (set foods)]
          (if (empty? left)
            (<! (msg$ "よし、先に進もう。" "前進"))
            (let [hp-max (get-player-data :hp-max)
                  food (first left)
                  pieces (count (filter (partial = food) foods))]
              (when-not (= hp-max (get-player-data :hp))
                (when (<! (choose$ (str (item-name food) "を"
                                        pieces "個持っている。\n"
                                        "食べる？")))
                  (loop []
                    (if (consume-from-inventory! food)
                      (let [old-hp (get-player-data :hp)
                            pow (inc (rand-int 8))
                            _ (vary-hp! pow false)
                            new-hp (get-player-data :hp)
                            msg (acms (str (item-name food) "を食べた！\n"
                                           "HPが" pow "回復した！"))]
                        (if (= hp-max new-hp)
                          (<! (msg$ msg "OK"))
                          (when-not (<! (choose$ msg "OK" "もう一個食べる"))
                            (recur))))
                      (<! (msg$ (str "もう" (item-name food) "を"
                                     "持ってなかった！")
                                "OK")))))
                (recur (rest left))))))))))


;;; ステータスを見て、必要であれば、各種の処理を行う
(defn- event-by-status$ []
  (go
    (let [hp (get-player-data :hp)
          hp-max (get-player-data :hp-max)]
      ;; TODO
      (cond
        ;; - HPが0なら、ゲームオーバー
        (not (pos? hp)) (<! (emit-game-over$))
        ;; - HPが一定割合以上減っていれば、アイテムを使って回復するかどうか
        (and
          (get-player-data :varied-hp?)
          (< (/ hp hp-max) heal-threshold)) (<! (query-heal$))
        :else nil))))





(register-game-event!
  :entrance-cell
  #(msg$ (str "ここは、おしいれの中の階段を降りてすぐの場所だ。\n"
              "細い通路がずっと先まで続いている。\n"
              "前に進むしかないみたいだ！"
              )
         "前進"))

;;; TODO: 鍵を持って扉を開いたら、このイベントが起こるようにする？(つまりこれは出口発見イベントではなく、扉イベントの一部になる)
(register-game-event!
  :goal-1$
  (fn []
    (go
      (set-img! "exit_b.png")
      (if (<! (choose$ (acms (str "これは出口だ！\n"
                                  "ここから外に出られそうだ！"))
                       "脱出する"
                       "無視して前進"))
        (do
          ;; TODO: cssの背景画像を屋外のものに変更する
          (flag-on! :game-is-finished)
          (flag-on! :clear-game)
          (<! (msg$ (str "長い冒険の末、ついにぼくは"
                         "おしいれから脱出した！\n"
                         "\n"
                         "ここはどこだろう？"
                         )
                    "脱出した！")))
        (<! (msg$ (acms (str "おしいれの探検はまだ続く…"
                             ""))
                  "前進"))))))

(register-game-event!
  :stair-up-1$
  (fn []
    (go
      (set-img! "stair_up.png")
      (if (<! (choose$ (acms "上の階に上がる階段がある。")
                       "階段を上がる"
                       "無視して前進"))
        (do
          (swap! the-game-data update-in [:floor] dec)
          (swap! the-game-data assoc-in [:step] 0)
          (update-title-status!)
          (<! (msg$ (str "階段を上がった。")
                    "前進")))
        true))))

(register-game-event!
  :stair-down-1$
  (fn []
    (go
      (set-img! "stair_down.png")
      (if (<! (choose$ (acms "下の階へと降りる階段がある。")
                       "階段を降りる"
                       "無視して前進"))
        (do
          (swap! the-game-data update-in [:floor] inc)
          (swap! the-game-data assoc-in [:step] 0)
          (update-title-status!)
          (<! (msg$ (str "階段を降りた。")
                    "前進")))
        true))))

(register-game-event!
  :normal-1$
  #(msg$ (acms "特にかわったものはないようだ。") "前進"))

(register-game-event!
  :normal-2$
  #(msg$ (acms "ここはうすぐらい…。") "前進"))

(register-game-event!
  :normal-3$
  #(msg$ (acms "奥から風が吹いてきているようだ。") "前進"))

(register-game-event!
  :damage-1$
  (fn []
    (go
      (let [dam (inc (rand-int 2))]
        (vary-hp! (- dam) true)
        (update-title-status!)
        (<! (msg$ (acms (str "すべってころんだ！\n"
                             dam "のダメージを受けた！"))
                  "痛い！"))
        (<! (event-by-status$))))))

(defn- gen-item-event [k]
  (fn []
    (let [filename (str (name k) ".png")]
      (set-img! filename)
      (add-to-inventory! k)
      (msg$ (acms (str (item-name k) "が落ちている。\n"
                       "拾った。"
                       ))
            "前進"))))

;;;
;;; items
;;;

(def ^:private item-name
  {:banana "バナナ"
   :orange "オレンジ"
   ;; TODO
   })

(def ^:private item-foods #{:orange :banana})

(register-game-event! :item-orange$ (gen-item-event :orange))
(register-game-event! :item-banana$ (gen-item-event :banana))



;;;
;;; define floors
;;;

(register-game-event-table!
  ;; フロア番号
  1
  ;; ステップ数固定のイベント(あれば)
  {0 :entrance-cell}
  ;; ランダムイベントのテーブル(rand-nthで選択される)
  [;:goal-1$ ; for debug
   :stair-down-1$
   :normal-1$ :normal-2$ :normal-3$
   :item-orange$ :item-banana$
   :damage-1$
   ])

(register-game-event-table!
  2
  {0 :entrance-cell}
  [:goal-1$ ; for debug
   :stair-up-1$ ;:stair-down-1$
   :normal-1$ :normal-2$ :normal-3$
   :damage-1$
   ])







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
                "\n"
                "まだ未完成バージョンです(すみません)。\n"
                "\n")
           "『おしいれクエスト』を開始する")))

(defn- opening-demo$ "オープニングデモ" []
  (go
    (set-img! "door2.jpg")
    (<! (msg$ "ぼくの家には、奇妙なおしいれがある。"))
    (set-img! "closet1.png")
    (<! (msg$ "今日は、○○を探しにおしいれに入った。"))
    (set-img! "black.png")
    (<! (msg$ "そしたら、急におしいれがしまり、開かなくなってしまった。"))
    (set-img! "stair_down.png")
    (<! (msg$ (str "おしいれの奥を見てみると、謎の階段があった。")))
    (set-img! "black.png")
    (<! (msg$ (str "ぼくは階段を降りてみた。\n"
                   "そこは通路のような場所で、"
                   "ずっと先まで道になっているようだ。\n"
                   "ここから他のところにつながっているかもしれない。")))
    (hide-img!)
    (<! (msg$ "こうして、ぼくの冒険がはじまった！" "プレイ開始"))
    true))

(defn- game-over$ []
  (go
    (set-img! "gameover.png")
    (set-title! "ゲームオーバー")
    (<! (msg$ "おわってしまった" "再挑戦"))))

(defn- ending$ []
  (go
    (set-img! "oshimai.png")
    (set-title! "おしまい")
    (<! (swal$ {:text "おわってしまった"
                :confirmButtonText "完"
                :closeOnConfirm true}))))

;;;
;;; main
;;;

(defn ^:export main []
  (go
    ;; データの初期化
    (init!)
    ;; 起動ダイアログ表示
    (<! (boot-msg$))
    ;; オープニングデモを表示させるかどうかの分岐
    (when (<! (choose$ "オープニングデモを見ますか？"))
      (<! (opening-demo$)))
    ;; ゲームのメインループ
    ;; NB: メインループ内では任意のタイミングにてゲームが終了し得るので、
    ;;     こまめに(flag :game-is-finished)をチェックしなくてはならない
    (loop []
      ;; ステータスチェックおよびそれに付随する処理
      (<! (event-by-status$))
      ;; ステータス表示の更新
      (update-title-status!)
      (when-not (flag :game-is-finished)
        ;; このステップでのイベントを実行
        (<! (invoke-step-event$))
        ;; 一歩進む
        (swap! the-game-data update-in [:step] inc))
      ;; ゲーム終了時の判定
      (if-not (flag :game-is-finished)
        (do
          (<! (async/timeout 1)) ; 念の為の無限ループ避け用
          (recur))
        (if (flag :clear-game)
          (<! (ending$)) ; ゲームクリア。そのままメインループを抜け終了
          (do
            (<! (game-over$)) ; ゲームオーバー
            (init!) ; データ初期化してスタート地点から再プレイ
            (recur)))))))



