package rk.or.core

/** Common shape of Point, Segment and Face: hover/select are duck-typed in the JS source. */
interface Selectable {
    var hover: Boolean
    var select: Boolean
}
