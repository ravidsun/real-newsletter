package com.realnewsletter.model;

/**
 * Application event published after a new article is successfully saved.
 */
public record NewArticleEvent(Article article) {
}

