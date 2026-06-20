package com.example.image.data.repository

import com.example.image.data.model.GalleryImage
import kotlin.math.roundToInt

object GalleryFactory {
    fun create(seed: Int): List<GalleryImage> {
        val ratios = listOf(0.67f, 0.72f, 0.76f, 0.82f, 0.9f, 1.0f, 1.18f, 1.35f)
        return (1..100).map { index ->
            val ratio = ratios[(index + seed).mod(ratios.size)]
            val profile = profileFor(index, ratio)
            val thumbWidth = 360
            val thumbHeight = (thumbWidth / ratio).roundToInt().coerceAtLeast(240)
            val fullWidth = 1440
            val fullHeight = (fullWidth / ratio).roundToInt().coerceAtLeast(900)
            val imageSeed = "material-$seed-$index"
            GalleryImage(
                id = "seed_${seed}_$index",
                photoDate = "2026-06-${((index - 1) % 28 + 1).toString().padStart(2, '0')}",
                thumbUrl = "https://picsum.photos/seed/$imageSeed/$thumbWidth/$thumbHeight",
                fullUrl = "https://picsum.photos/seed/$imageSeed/$fullWidth/$fullHeight",
                aspectRatio = ratio,
                contentTitle = profile.title,
                category = profile.category,
                useCase = profile.useCase,
                keywords = profile.keywords
            )
        }
    }

    private fun profileFor(index: Int, ratio: Float): MaterialProfile {
        val category = categories[(index - 1).mod(categories.size)]
        val mood = moods[(index * 3).mod(moods.size)]
        val scene = scenes[(index * 7).mod(scenes.size)]
        val layout = when {
            ratio < 0.86f -> "竖版"
            ratio > 1.1f -> "横版"
            else -> "方图"
        }
        val title = "$layout$category · $scene"
        val keywords = (listOf(category, mood, scene, layout) + categoryKeywords.getValue(category)).distinct()
        return MaterialProfile(
            title = title,
            category = category,
            useCase = "$scene / $mood",
            keywords = keywords
        )
    }

    private data class MaterialProfile(
        val title: String,
        val category: String,
        val useCase: String,
        val keywords: List<String>
    )

    private val categories = listOf(
        "PPT背景",
        "手机壁纸",
        "文章配图",
        "封面图",
        "社交配图",
        "留白素材",
        "产品展示",
        "灵感拼贴",
        "头像背景",
        "海报底图"
    )

    private val moods = listOf(
        "清爽",
        "沉稳",
        "温柔",
        "高级感",
        "自然",
        "通勤",
        "活力",
        "安静",
        "科技感",
        "生活感"
    )

    private val scenes = listOf(
        "汇报开场",
        "知识笔记",
        "公众号首图",
        "小红书封面",
        "桌面整理",
        "作品集",
        "活动预告",
        "朋友圈配图",
        "灵感板",
        "品牌氛围"
    )

    private val categoryKeywords = mapOf(
        "PPT背景" to listOf("PPT", "演示", "汇报", "背景", "横版背景"),
        "手机壁纸" to listOf("壁纸", "手机", "锁屏", "竖图", "屏保"),
        "文章配图" to listOf("文章", "博客", "公众号", "配图", "插图"),
        "封面图" to listOf("封面", "标题图", "首图", "视觉焦点"),
        "社交配图" to listOf("朋友圈", "小红书", "社交", "分享", "日常"),
        "留白素材" to listOf("留白", "文字叠加", "排版", "干净", "背景"),
        "产品展示" to listOf("产品", "展示", "详情页", "电商", "质感"),
        "灵感拼贴" to listOf("灵感", "拼贴", "情绪板", "参考", "素材"),
        "头像背景" to listOf("头像", "个人主页", "背景图", "社交资料"),
        "海报底图" to listOf("海报", "活动", "宣传", "预告", "KV")
    )
}
