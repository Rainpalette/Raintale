package io.github.rainpalette.raintale.ui.tutorial

/**
 * 《雨中逸事》开场教程脚本（合并稿 v1）。
 *
 * **改台词 / 加分支只动本文件。** 结构：前奏（占位照片）→ 初遇问候 → 菜单 hub
 * →（教程 / 问答 / 直接开始）。回跳只回菜单，不重复问候。录影模式暂不列入（功能待定）。
 *
 * 占位：前奏用 [Beat.Photo] 的 placeholder 文本占位；日后换成几张交代"巴士→雨→站台"的照片，
 * 把 placeholder 换成 imageRes 即可（引擎已支持）。
 */
object TutorialContent {

    const val START = "prologue"
    const val SPEAKER = "米娜"

    val script: TutorialScript = mapOf(
        // 〇 前奏：无对白，占位照片，轻点翻过 → 初遇
        "prologue" to listOf(
            Beat.Photo(placeholder = "前奏 · 占位照片\n（日后换成交代「巴士 → 雨 → 站台」的几张照片）"),
            Beat.Goto("greeting"),
        ),

        // 一 初遇：问候（首次经过，回跳不重复）→ 菜单
        "greeting" to listOf(
            Beat.Say(SPEAKER, "……你好。欢迎来到异空间。"),
            Beat.Say(SPEAKER, "想必你也是因为各种原因，才来到这里的。有什么想问的、想做的，请自便吧。"),
            Beat.Goto("menu"),
        ),
        "menu" to listOf(
            Beat.Menu(
                options = listOf(
                    Choice("我能在这里做什么？（教程）", "tutorial"),
                    Choice("我想了解这里的事……（了解故事）", "lore"),
                    Choice("我这就开始吧。（直接开始）", "leave"),
                ),
            ),
        ),

        // 二 教程：相机与各模式
        "tutorial" to listOf(
            Beat.Say(SPEAKER, "你有一架相机呢。"),
            Beat.Say(SPEAKER, "异空间会根据你的认知进行投影——现实的空间、记忆里的人……它能把它们整合到一起。不过，不是所有东西都能被正确地投影出来。"),
            Beat.Say(SPEAKER, "在这里，你能看见虚拟与现实的融合。既然你有相机，不如就把这些奇景记录下来吧。"),
            Beat.Say(SPEAKER, "【普通模式】与角色一同合照；完成后，角色会对照片做出回应。"),
            Beat.Say(SPEAKER, "【留言模式】与角色合照后，可留下一句话作补充，角色会结合照片与留言回应。"),
            Beat.Say(SPEAKER, "【实时点评】与角色一同互动，它会以一定频率根据画面回应。"),
            Beat.Say(SPEAKER, "【人像模式】合照时可切换角色与真人的前后位置。低帧率下延迟较明显，移动可能出现残影。"),
            Beat.Say(SPEAKER, "【陪伴模式】与自定义角色拍照。它不会出现在照片里、也没有语音，只按你设定的性格回应。"),
            Beat.Say(SPEAKER, "【相册】拍下的照片都在这里。你可以回看当时角色留下的评价，或让它再评价一次。"),
            Beat.Say(SPEAKER, "有需要我帮忙，就叫我。我会一直在这里的。"),
            Beat.Goto("menu"),
        ),

        // 三 问答
        "lore" to listOf(
            Beat.Say(SPEAKER, "请说。"),
            Beat.Menu(
                options = listOf(
                    Choice("你叫什么名字？", "lore_name"),
                    Choice("这里是哪里？你为什么在这儿？", "lore_place"),
                    Choice("（没什么了）", "menu"),
                ),
            ),
        ),
        "lore_name" to listOf(
            Beat.Say(SPEAKER, "我叫米娜。虽然常年待在这片空间里，但我和你一样，只是个普通人。"),
            Beat.Goto("lore"),
        ),
        "lore_place" to listOf(
            Beat.Say(SPEAKER, "异空间。这里会因人的情感而变化，投影出往昔的记忆——这个巴士站，就是我记忆里的投影。"),
            Beat.Say(SPEAKER, "至于我为什么在这儿……你也是从巴士上下来的。我知道那车最终通往哪里。我不想去，却也回不到现实，只好留在这里了。"),
            Beat.Goto("lore"),
        ),

        // 离开 → 进相机
        "leave" to listOf(
            Beat.Say(SPEAKER, "嗯。去吧，我在这儿看着。"),
            Beat.End,
        ),
    )
}
