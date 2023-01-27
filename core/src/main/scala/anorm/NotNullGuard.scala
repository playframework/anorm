/*
 * Copyright (C) from 2022 The Play Framework Contributors <https://github.com/playframework>, 2011-2021 Lightbend Inc. <https://www.lightbend.com>
 */

package anorm

/**
 * Marker trait to indicate that even if a type T accept null as value,
 * it must be refused in some Anorm context.
 */
trait NotNullGuard
