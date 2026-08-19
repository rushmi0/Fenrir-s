import {useState} from 'react'
import {Link} from 'react-router-dom'
import Chamfer from '@/components/ui/Chamfer'
import mascotImg from '@/assets/images/fenrir_pixel_art_v2-bg-remove-cx_fVNSh.png'
import dogBark from '@/assets/dog-CytXpvEm.mp3'
import styles from './Hero.module.css'

export default function Hero() {
    const [isJumping, setIsJumping] = useState(false)

    const handleMascotClick = () => {
        new Audio(dogBark).play().then(r => console.log(r))
        setIsJumping(true)
    }

    return (
        <section className={styles.hero}>
            <div>
                <Chamfer size={6} className={styles.eyebrow}>
                    OPEN SOURCE // NOSTR PROTOCOL
                </Chamfer>
                <h1 className={styles.heading}>
                    <span className={styles.accentText}>Fenrir's</span>
                    <br/>
                    <span className={styles.inkText}>Nostr Relay</span>
                </h1>
                <p className={styles.subhead}>
                    A lightweight Nostr relay written for speed.
                    <br/>
                    Run your own relay. Own your data.
                </p>
                <div className={styles.actions}>
                    <Chamfer as={Link} to="/login" size={10} className={styles.primaryButton}>
                        Start &gt;
                    </Chamfer>
                    <Chamfer as="a" href="#features" size={10} className={styles.secondaryButton}>
                        Learn More
                    </Chamfer>
                </div>
            </div>
            <div className={styles.artWrap}>
                <Chamfer size={24} className={styles.artBackdrop}/>
                <img
                    src={mascotImg}
                    alt="Fenrir pixel-art husky mascot"
                    className={`${styles.mascot} ${isJumping ? styles.mascotJump : ''}`}
                    onClick={handleMascotClick}
                    onAnimationEnd={() => setIsJumping(false)}
                />
            </div>
        </section>
    )
}