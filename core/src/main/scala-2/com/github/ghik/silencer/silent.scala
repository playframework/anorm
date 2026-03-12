/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package com.github.ghik.silencer

/**
 * No-op stub replacing the silencer library annotation for Scala 2.x.
 * Warnings are suppressed via `-Wconf` scalac options instead.
 */
class silent(s: String = "") extends scala.annotation.StaticAnnotation
