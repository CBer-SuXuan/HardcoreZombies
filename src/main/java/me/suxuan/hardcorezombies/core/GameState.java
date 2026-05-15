package me.suxuan.hardcorezombies.core;

public enum GameState {
	/**
	 * 房间已创建，正在等待玩家加入
	 */
	WAITING,
	/**
	 * 人数满足要求，倒计时开始
	 */
	STARTING,
	/**
	 * 游戏进行中
	 */
	IN_GAME,
	/**
	 * 游戏结束（全员阵亡或通关），展示结算界面并准备销毁
	 */
	ENDING
}