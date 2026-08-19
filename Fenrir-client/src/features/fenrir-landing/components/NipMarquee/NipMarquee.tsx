import Chamfer from '@/components/ui/Chamfer'
import checkIcon from '@/assets/icons/check.svg'
import { NIPS } from '../../data'
import styles from './NipMarquee.module.css'

interface NipMarqueeProps {
  speed: number
}

export default function NipMarquee({ speed }: NipMarqueeProps) {
  const loopedNips = [...NIPS, ...NIPS]

  return (
    <section id="nips" className={styles.strip}>
      <div className={styles.track} style={{ animationDuration: `${speed}s` }}>
        {loopedNips.map((nip, index) => (
          <Chamfer key={`${nip}-${index}`} size={10} className={styles.chip}>
            <img src={checkIcon} alt="" className={styles.check} />
            {nip}
          </Chamfer>
        ))}
      </div>
    </section>
  )
}