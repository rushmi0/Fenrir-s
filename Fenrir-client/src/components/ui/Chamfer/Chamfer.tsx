import type { ComponentPropsWithoutRef, CSSProperties, ElementType } from 'react'

export function chamferClipPath(size: number): string {
  return `polygon(${size}px 0, 100% 0, 100% calc(100% - ${size}px), calc(100% - ${size}px) 100%, 0 100%, 0 ${size}px)`
}

type ChamferOwnProps<T extends ElementType> = {
  as?: T
  size?: number
  style?: CSSProperties
}

type ChamferProps<T extends ElementType> = ChamferOwnProps<T> &
  Omit<ComponentPropsWithoutRef<T>, keyof ChamferOwnProps<T>>

/** Wraps its tag with the design's signature 45deg-cut top-left/bottom-right corners. */
export default function Chamfer<T extends ElementType = 'div'>({
  as,
  size = 12,
  style,
  ...rest
}: ChamferProps<T>) {
  const Component = as ?? 'div'
  return <Component style={{ ...style, clipPath: chamferClipPath(size) }} {...rest} />
}