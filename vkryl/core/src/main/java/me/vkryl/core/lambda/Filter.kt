package me.vkryl.core.lambda

/**
 * Date: 25/11/2018
 * Author: default
 */
interface Filter<T> {
  fun accept(value: T): Boolean
}