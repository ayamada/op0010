# `core.async`と押し入れクエスト

これは [Clojure Advent Calendar 2014](http://qiita.com/advent-calendar/2014/clojure) の九日目の記事です。


## Abstract

記事を全部読むのが面倒な人向けの要約です。

- 今回の記事の為に作成したブラゲ(ブラウザゲーム)が http://vnctst.tir.jp/op0010/ にあります。タイトルの「押し入れクエスト」とはこれの事です。最後まで遊ぶ必要はないので、とりあえずどんな感じに動いているかだけ簡単に見ておいてください。

- 上記ゲームのソースコードは [src/cljs/op0010/core.cljs](src/cljs/op0010/core.cljs) にあります。
    - この中の[main関数](src/cljs/op0010/core.cljs#L1)の中に、このゲームのシステム本体があり、そこを見てもらえれば ** 『`core.async`を使えば、このように非常に素直なコードでゲームのロジックが書き下せる』 ** という事が分かってもらえると思います。筆者がこの記事で訴えたい一番のポイントはここです。
        - (TODO: 記事の公開前に、行番号指定を`main`関数の位置にきちんと合わせる事)

- 今回の記事では`core.async`の解説はあまりしません。既に分かりやすい解説文書があるので、そちらを読むとよいでしょう。
    - http://qiita.com/ympbyc/items/ef697fa3c7228ad5d7c6
        - cljsとcore.asyncを使って、いわゆるroguelikeゲームのキー操作回りを分かりやすいコードで書く記事
    - http://qiita.com/mizchi/items/55386689d19a67baa62d
        - core.asyncの分かりやすい解説
    - https://speakerdeck.com/tnoda/yet-another-introduction-to-core-dot-async
        - この前の[烏丸Clojure](http://e6a302c89833f490f111a94ebc.doorkeeper.jp/events/17230)での、[@tnoda_](https://twitter.com/tnoda_)さんによるスライド
        - 英語だが、上記の二つの記事で省略されている、core.asyncの基本部分の解説があるので、基本部分に不安がある人は目を通しておくとよい
    - http://www.core-async.info/
        - 英語だがチュートリアルがある
        - [リファレンス](http://www.core-async.info/reference/apidocs)もある
            - このリファレンスの関数/マクロ一覧には「`function Clojure only`」の表記があるものがあり、この表記があるものはcljsでは利用できない。この表記は`core.async`の[公式リファレンス](http://clojure.github.io/core.async/)の方には全く存在しない為、公式リファレンスよりも優れている(というか公式リファレンス不便すぎ)
            - とは言え、今後もきちんとバージョンアップ追随されるか等の不安点や、このサイト自体がcljsで書かれていてちょっと重い等の不満点はある

- 「押し入れクエスト」ではUIの構成に、外部JSライブラリとして[sweetAlert](https://github.com/t4t5/sweetalert)を利用しています。
    - sweetAlertがどんなものかは、 http://tristanedwards.me/sweetalert にあるデモを見てください。要は、JavaScript標準の`alert()` `confirm()`の代替品です。
    - 現バージョンのsweetAlertでは以下の点に注意が必要です。
        1. 本物の`alert()` `confirm()`とは違い、制御(継続)がその場で止まらない
            - これについては`core.async`で適切にラッピングする事で使い勝手を改善できる。どのようにラッピングしているのは実際のコードを参照
        2. モーダルダイアログっぽさがいまいち不完全で、マウスからの操作こそきちんと遮断されているものの、キーボード操作(tabキーで移動して決定する等)の遮断がきちんと実現できてない
            - これについては仕方がないので、とりあえず今回の「押し入れクエスト」では「コンテンツ本体を空にし、ダイアログのみで内容を構成する」という手法で問題を回避した

- 「自分も ~~ しょぼい ~~ ブラゲを手早く作りたい！」という人がいれば、このリポジトリをcloneして改造する事ができます。MIT Licenseとします。
    - なんでMITなのかと言うと、組み込んだsweetAlertのライセンスがMITだった為、それに合わせた為。
    - この記事の残り部分は、そういう人の為の開発手順の比率が大きいです(というか実際に筆者が作成した手順を書き記しただけのもの)。
        - 非常にシンプルな作りなのと、手順をいちいち書いたので、Clojureプログラマであればcljs固有のノウハウ等はなくても普通にいじれると思います。レッツ作成！

以上です。


## Introduction

こんにちわ。ここ最近は[株式会社テンクー](https://xcoo.co.jp/)にてお仕事をさせていただいている[山田](http://tir.jp/)と申します。よろしくおねがいします。

上で書いている通り、以下では「押し入れクエスト」を作成した手順を示していきます。


## プロジェクトの作成

まず、cljs向けの `project.clj` を用意する。

きちんとした本格的なテンプレを用意したい場合は、[chestnut](https://github.com/plexus/chestnut)のがよい(らしい)

- http://tnoda-clojure.tumblr.com/post/102891865037/plexus-chestnut

また、自分が以前に[烏丸Clojure](http://e6a302c89833f490f111a94ebc.doorkeeper.jp/events/17230)でスライドを作った時に一緒に書いたものが http://doc.tir.ne.jp/misc/karasumaclj/project.clj にある。

が、今回はどちらも使わず、極力シンプルなものを用意した。

実際の内容については、同梱の [project.clj](project.clj) を参照。


## index.html

今回は極力シンプルに行きたいので、`ring`等のhttpdも使わず、直接 `index.html` を用意する事にした。

- [resources/public/index.html](resources/public/index.html) にある。
    - この中で参照している `sweet-alert.css` と `sweet-alert.min.js` は、sweetAlertの配布物から取ってきた。
    - `cljs.js` は前述の `project.clj` にてビルドされる。

上記の `resources/public/` という場所は、 [ring](https://github.com/ring-clojure/ring) でhttpdを動かした時のデフォルトのコンテンツ置き場になる。今回は`ring`は使わないが、一応揃えておいた。

- よって、 `index.html` 以外に置きたいファイルがある場合も `resources/public/` 内に置けばよい。最終的にデプロイする時には、このディレクトリをデプロイする形になる。
    - 今回、ゲーム内で利用する画像は `resources/public/assets/img/` 内に置く事とした。その結果、これらの画像に`index.html`から参照する場合は `assets/img/hoge.png` 等のように指定する事になる。


## ビルドプロセスの起動

本体のソースは [src/cljs/op0010/core.cljs](src/cljs/op0010/core.cljs) に書く。

- `index.html` の中に `<body onload="op0010.core.main();">` と書いてあり、これをClojure流に翻訳するならば「`(op0010.core/main)`」が実行されるのに相当する。よって、前述の`src/cljs/op0010/core.cljs`内に`main`関数を書く、という事になる。また、この`main`関数には「JavaScript側から参照される」印として、`^:export`メタ属性を付与しておく。

別コンソールを開き `lein cljsbuild clean && lein cljsbuild auto` を実行しておく。これで、上記のソースファイルがエディタから更新される毎に`cljs.js`が動的に生成され直すので、あとは`index.html`をブラウザで開いて適当にリロードしつつ動作を見ながら、インクリメンタル開発を行う。

- このビルドプロセスだが、マクロ追加だか何だかのタイミングでコンパイル等がおかしくなるっぽい事があるので、たまにctrl-Cで停止させて再度 `lein cljsbuild clean && lein cljsbuild auto` を実行し直した方がよい(この為に、最初に明示的にcleanするようにしている)。
    - このあたりのバッドノウハウは、以前に http://vnctst.tir.jp/karasumaclj/ にも少し書いているので、興味のある人は目を通しておいてもよい

なお、前述の[chestnut](https://github.com/plexus/chestnut)を使えば、いちいちブラウザをリロードしなくても、普通にnREPL経由でエディタからREPL接続が使えたりする。が、今回はchestnutは使っていないので、REPLなしでの開発手順となっている。


## sweetAlertを呼ぶラッパーを書く

今回はsweetAlertだけを使ってUIを作成する。
しかしそのままでは扱いづらいので、以下の仕様のラッパー関数を用意する。

- 引数としてmapを受け取り、デフォルト値のmapにmergeしたものをjs-obj化してからsweetAlertに渡す
- 実行結果として、`core.async`のチャンネルを返す。

この関数には [swal$](src/cljs/op0010/core.cljs#L1) という名前を付けた。

- (TODO: 記事の公開前に、行番号指定を`swal$`関数の位置にきちんと合わせる事)


## ゲーム本体を作成

TODO: 続きはあとで


