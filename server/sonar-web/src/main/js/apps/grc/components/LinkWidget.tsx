import React from 'react';
import { Link } from 'react-router';
interface Props{
    link:string
}
export default function LinkWidget(props:Props) {
    const {link} = props;
    return(<>
        <div className="link-widget">
            <Link to={link}>
                <img src="/images/more-details.svg"></img>
            </Link>
        </div>
    </>);
}