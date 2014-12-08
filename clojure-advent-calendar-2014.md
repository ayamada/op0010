# `core.async`とおしいれクエスト

これは [Clojure Advent Calendar 2014](http://qiita.com/advent-calendar/2014/clojure) の九日目の記事です。


## Abstract

記事を全部読むのが面倒な人向けの要約です。

- 今回の記事の為に作成したブラゲ(ブラウザゲーム)が http://vnctst.tir.jp/op0010/ にあります。タイトルの「おしいれクエスト」とはこれの事です。最後まで遊ぶ必要はないので、とりあえずどんな感じに動いているかだけ簡単に見ておいてください。

- 上記ゲームのソースコードは [src/cljs/op0010/core.cljs](src/cljs/op0010/core.cljs) にあります。
    - この中の[main関数](src/cljs/op0010/core.cljs#L1)の中に、このゲームのシステム本体があり、そこを見てもらえれば ** 『`core.async`を使えば、このように非常に素直なコードでゲームのロジックが書き下せる』 ** という事が分かってもらえると思います。筆者がこの記事で訴えたい一番のポイントはここです。
        - (TODO: 記事の公開前に、行番号指定を`main`関数の位置にきちんと合わせる事)
    - ゲームの状態遷移は、よくあるwebサービスや普通のアプリケーションソフトの状態遷移とは比べ物にならないぐらい、とんでもなく複雑になる事が多いです。しかし、`core.async`を上手く使えば、上記のように素直に書き下す事ができます。`core.async`素晴らしい。

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

- 「おしいれクエスト」ではUIの構成に、外部JSライブラリとして[sweetAlert](https://github.com/t4t5/sweetalert)を利用しています。
    - sweetAlertがどんなものかは、 http://tristanedwards.me/sweetalert にあるデモを見てください。要はJavaScript標準の`alert()` `confirm()`の代替品です。
    - 現バージョンのsweetAlertでは以下の点に注意が必要です。
        1. 本物の`alert()` `confirm()`とは違い、制御(継続)がその場で止まらない
            - これについては`core.async`で適切にラッピングする事で使い勝手を改善できる。どのようにラッピングしているのは実際のコードを参照
        2. モーダルダイアログっぽさがいまいち不完全で、マウスからの操作こそきちんと遮断されているものの、キーボード操作(tabキーで移動して決定する等)の遮断がきちんと実現できてない
            - これについては仕方がないので、とりあえず今回の「おしいれクエスト」では「コンテンツ本体を空にし、ダイアログのみで内容を構成する」という手法で問題を回避した
        3. カスタマイズの自由度はあまりない
            - 指定画像サイズ変更の引数があるが、なんか上手く動いてない
            - もちろん、jsソースやcssをいじれば好きにはできるが…

- 「自分も ~~ しょぼい ~~ ブラゲを手早く作りたい！」という人がいれば、このリポジトリをcloneして改造する事ができます。
    - シンプルな作りなのと、作成手順を以下に書いたので、Clojureプログラマであればcljs固有のノウハウ等はなくてもいじれるかもしれません。
    - ライセンスについては[README.md](README.md)を参照してください。

以上です。


## Introduction

こんにちわ。ここ最近は[株式会社テンクー](https://xcoo.co.jp/)にて、Clojureの仕事をさせてもらっている[山田](http://tir.jp/)と申します。

以下では、実際に「おしいれクエスト」を作成した手順を示していきます。


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
    - 今回、ゲーム内で利用する画像は `resources/public/assets/img/` 内に置く事とした。その結果、これらの画像を`index.html`から参照する場合は `assets/img/hoge.png` 等のように指定する事になる。


## ビルドプロセスの起動

本体のソースは [src/cljs/op0010/core.cljs](src/cljs/op0010/core.cljs) に書く。

- `index.html` の中に `<body onload="op0010.core.main();">` と書いてあり、これをClojure流に翻訳するならば「`(op0010.core/main)`」が実行されるのに相当する。よって、前述の`src/cljs/op0010/core.cljs`内に`main`関数を書く、という事になる。また、この`main`関数には「JavaScript側から参照される」印として、`^:export`メタ属性を付与しておく。

別コンソールを開き `lein cljsbuild clean && lein cljsbuild auto` を実行しておく。これで、上記のソースファイルがエディタから更新される毎に`cljs.js`が動的に生成され直すので、あとは`index.html`をブラウザで開いて適当にリロードしつつ動作を見ながら、インクリメンタル開発を行う。

- このビルドプロセスだが、マクロ追加だか何だかのタイミングでコンパイル等がおかしくなるっぽい事があるので、たまにctrl-Cで停止させて再度 `lein cljsbuild clean && lein cljsbuild auto` を実行し直した方がよい(この為に、最初に明示的にcleanするようにしている)。
    - このあたりのバッドノウハウは、以前に http://vnctst.tir.jp/karasumaclj/ にも少し書いているので、興味のある人は目を通しておいてもよい

なお、前述の[chestnut](https://github.com/plexus/chestnut)を使えば、いちいちブラウザをリロードしなくても反映されたり、普通にnREPL経由でエディタからページに対してREPL接続が使えたりする。が、今回はchestnutは使っていないので、REPLなしでの開発手順となっている。


## sweetAlertを呼ぶラッパーを書く

今回はsweetAlertだけを使ってUIを作成する。
しかしそのままでは扱いづらいので、以下の仕様のラッパー関数を用意する。

- 引数としてmapを受け取り、デフォルト値のmapにmergeしたものをjs-obj化してからsweetAlertに渡す
- 実行結果として、`core.async`のチャンネルを返す。

この関数には [swal$](src/cljs/op0010/core.cljs#L1) という名前を付けた。

- (TODO: 記事の公開前に、行番号指定を`swal$`関数の位置にきちんと合わせる事)


## ゲーム本体を作成

あとは実際にゲーム自体の内容を作っていくだけだ！

一番大変な部分だが、この部分はもはやClojureとは全然関係ないので、この記事では省略。

ここで、`core.async`を最大限に利用する。

- 「`go`ブロックがチャンネルを返す」という性質が意外と重要で、この性質によって「別の`go`ブロックの返り値」を`<!`で素直に受け取る事ができる。
    - このコード(`defn`や`fn`のすぐ内側に`go`を書くコード)は結構多いので、`go-loop`のようにマクロ化してもよいと思う


## デプロイする

ゲーム本体が完成し、動作確認も一通り終わったなら、完成したゲームをどこかのwebサーバに設置して公開する。

基本的には先に書いたように `resources/public/` 配下を丸ごとコピーするだけだが、その前にcljsbuildの最適化オプションを変更しておいた方がよい。

- 具体的には以下の手順を実行する
    1. ビルドプロセスが起動しているなら、停止させておく
    2. [project.clj](project.clj) の `:optimizations :whitespace #_:simple` になっているところを `:optimizations #_:whitespace :simple` に変更する
    3. `lein cljsbuild clean && lein cljsbuild once` を実行する
    4. `resources/public/` 配下を丸ごとデプロイ先にコピーする
    5. デプロイが完了したので、(2)で変更した`project.clj`を元に戻す
    6. 念の為 `lein cljsbuild clean` も実行しておく

- 上記の手順にて、いちいち`project.clj`を書き換えているが、これは本来であればprofileを分けるべき。今回は極力シンプルにする方針にした悪影響でこうなってしまったが…

- なお、`:optimizations`オプションにはもっと最適化する`:advanced`オプションもあるのだが、こっちは`:externs`指定がほぼ必須になったり、色々と大変なので最初の内はおすすめしない。


## 完成

以上です。おつかれさまでした。

この後は以下を検討してもよいかもしれません。

- SEやBGMをつけたり、セーブ機能をつけたりの改善をしたりしてみる。sweetAlertを、もっときちんとしたUIを提供しているライブラリに変更したりしてみる
- 次回の[ニコニコ自作ゲームフェス](http://ch.nicovideo.jp/indies-game)が始まったら登録してみる。他のゲーム系イベントも探して参加してみる
- `core.async` の実際の使い方が分かったので、別の新しいプロジェクトを作り、また別のゲーム/アプリを作成してみる。別にcljsに限定する必要もない


